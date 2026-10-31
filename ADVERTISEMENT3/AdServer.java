import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class AdServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Core CRUD & Search routes
        server.createContext("/api/ads", AdServer::adsHandler);
        server.createContext("/api/ads/update", AdServer::updateAdHandler);
        server.createContext("/api/ads/delete", AdServer::deleteAdHandler);

        // PostgreSQL Functions, Triggers, Views & Aggregates
        server.createContext("/api/functions/expire", AdServer::expireAdsHandler);
        server.createContext("/api/logs", AdServer::logsHandler);
        server.createContext("/api/stats", AdServer::statsHandler);
        server.createContext("/api/reports", AdServer::reportsHandler);
        server.createContext("/api/payments", AdServer::paymentsHandler);
        server.createContext("/api/meta", AdServer::metaHandler);
        server.createContext("/api/advertisers", AdServer::advertisersHandler);
        server.createContext("/api/campaigns", AdServer::campaignsHandler);
        server.createContext("/api/campaigns/details", AdServer::campaignDetailsHandler);
        server.createContext("/api/campaigns/delete", AdServer::campaignDeleteHandler);
        server.createContext("/api/runtime", AdServer::runtimeHandler);

        // Database connection test & config
        server.createContext("/api/db/status", AdServer::dbStatusHandler);
        server.createContext("/api/db/connect", AdServer::dbConnectHandler);

        // Static files (index.html, styles.css, script.js)
        server.createContext("/", AdServer::filesHandler);

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        WebAdRuntime.start();
        Runtime.getRuntime().addShutdownHook(new Thread(WebAdRuntime::stop, "ad-runtime-shutdown"));
        server.start();

        System.out.println("==================================================");
        System.out.println(" Adwise PostgreSQL Server Running!");
        System.out.println(" URL: http://localhost:" + port);
        System.out.println(" Connected DB URL: " + DBConnection.URL);
        System.out.println(" Press Ctrl+C to stop.");
        System.out.println("==================================================");
    }

    // CORS and response helpers
    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE, PUT");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendText(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void runtimeHandler(HttpExchange ex) throws IOException {
        sendJson(ex, 200,
            "{\"hash_map_cached_ads\":" + WebAdRuntime.cachedCount()
            + ",\"array_list_ordered_ads\":" + WebAdRuntime.orderedCount()
            + ",\"vector_latest_event\":" + q(WebAdRuntime.latestEvent()) + "}");
    }

    static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    static Map<String, String> parseParams(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod()) || "PUT".equalsIgnoreCase(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (body.startsWith("{") && body.endsWith("}")) {
                // simple JSON parser for flat key-value
                body = body.substring(1, body.length() - 1);
                String[] tokens = body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (String token : tokens) {
                    String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("^\"|\"$", "");
                        String val = kv[1].trim().replaceAll("^\"|\"$", "");
                        map.put(key, val);
                    }
                }
            } else if (!body.isEmpty()) {
                for (String pair : body.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        map.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return map;
    }

    // ----------------------------------------------------
    // GET & POST /api/ads
    // ----------------------------------------------------
    static void adsHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            insertAd(ex);
            return;
        }

        // GET: Search / list all advertisements
        Map<String, String> params = parseParams(ex);
        String search = params.getOrDefault("search", "").trim().toLowerCase();
        String statusFilter = params.getOrDefault("status", "").trim();
        String typeFilter = params.getOrDefault("type", "").trim();

        StringBuilder sql = new StringBuilder(
            "SELECT a.ad_id, a.title, a.description, a.budget, a.status, a.ad_type, a.audience, " +
            "       a.advertiser_id, COALESCE(r.name, 'Unknown') AS advertiser_name, " +
            "       a.platform_id, COALESCE(p.platform_name, 'Unknown') AS platform_name, " +
            "       a.start_date, a.end_date, " +
            "       (a.budget * 0.18) AS tax, " +
            "       (a.budget * 1.18) AS total_cost, " +
            "       CASE WHEN a.end_date IS NULL THEN NULL ELSE GREATEST(a.end_date - CURRENT_DATE, 0) END AS days_left, " +
            "       CASE " +
            "           WHEN COALESCE(SUM(pay.amount), 0) >= a.budget THEN 'Fully Paid' " +
            "           WHEN COALESCE(SUM(pay.amount), 0) > 0 THEN 'Partially Paid' " +
            "           ELSE 'Unpaid' " +
            "       END AS payment_status, " +
            "       CASE " +
            "           WHEN a.end_date IS NULL OR a.end_date >= CURRENT_DATE THEN 'Showing' " +
            "           ELSE 'Expired' " +
            "       END AS show_status, " +
            "       COALESCE(SUM(pay.amount), 0) AS total_paid, " +
            "       a.updated_at " +
            "FROM advertisement a " +
            "LEFT JOIN advertiser r ON a.advertiser_id = r.advertiser_id " +
            "LEFT JOIN platform   p ON a.platform_id   = p.platform_id " +
            "LEFT JOIN payment  pay ON pay.ad_id       = a.ad_id " +
            "WHERE 1=1 "
        );

        if (!statusFilter.isEmpty() && !"all".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND LOWER(a.status) = LOWER('").append(statusFilter.replace("'", "''")).append("')");
        }
        if (!typeFilter.isEmpty() && !"all".equalsIgnoreCase(typeFilter)) {
            sql.append(" AND LOWER(a.ad_type) = LOWER('").append(typeFilter.replace("'", "''")).append("')");
        }
        if (!search.isEmpty()) {
            String s = search.replace("'", "''");
            sql.append(" AND (LOWER(a.title) LIKE '%").append(s).append("%' ")
               .append(" OR LOWER(COALESCE(a.description,'')) LIKE '%").append(s).append("%' ")
               .append(" OR LOWER(COALESCE(r.name,'')) LIKE '%").append(s).append("%' ")
               .append(" OR LOWER(COALESCE(p.platform_name,'')) LIKE '%").append(s).append("%') ");
        }

        sql.append(" GROUP BY a.ad_id, a.title, a.description, a.budget, a.status, a.ad_type, a.audience, " +
                   "          a.advertiser_id, r.name, a.platform_id, p.platform_name, a.start_date, a.end_date, a.updated_at " +
                   " ORDER BY a.ad_id ASC");

        StringBuilder json = new StringBuilder("[");
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql.toString())) {

            boolean first = true;
            while (rs.next()) {
                WebAdRuntime.cacheRow(rs);
                if (!first) json.append(",");
                first = false;
                String days = rs.getString("days_left");
                json.append("{")
                    .append("\"id\":").append(rs.getInt("ad_id"))
                    .append(",\"title\":").append(q(rs.getString("title")))
                    .append(",\"description\":").append(q(rs.getString("description")))
                    .append(",\"budget\":").append(rs.getDouble("budget"))
                    .append(",\"tax\":").append(rs.getDouble("tax"))
                    .append(",\"total_cost\":").append(rs.getDouble("total_cost"))
                    .append(",\"status\":").append(q(rs.getString("status")))
                    .append(",\"ad_type\":").append(q(rs.getString("ad_type")))
                    .append(",\"audience\":").append(q(rs.getString("audience")))
                    .append(",\"advertiser_id\":").append(rs.getInt("advertiser_id"))
                    .append(",\"advertiser_name\":").append(q(rs.getString("advertiser_name")))
                    .append(",\"platform_id\":").append(rs.getInt("platform_id"))
                    .append(",\"platform_name\":").append(q(rs.getString("platform_name")))
                    .append(",\"days\":").append(days == null ? "null" : days)
                    .append(",\"payment_status\":").append(q(rs.getString("payment_status")))
                    .append(",\"show_status\":").append(q(rs.getString("show_status")))
                    .append(",\"total_paid\":").append(rs.getDouble("total_paid"))
                    .append(",\"start_date\":").append(q(rs.getString("start_date")))
                    .append(",\"end_date\":").append(q(rs.getString("end_date")))
                    .append(",\"updated_at\":").append(q(rs.getString("updated_at")))
                    .append("}");
            }
            json.append("]");
            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            System.err.println("SQL error in /api/ads: " + e.getMessage());
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // INSERT Advertisement
    // ----------------------------------------------------
    static void insertAd(HttpExchange ex) throws IOException {
        Map<String, String> f = parseParams(ex);
        try {
            String title = f.getOrDefault("title", "").trim();
            String desc = f.getOrDefault("description", "").trim();
            double budget = Double.parseDouble(f.getOrDefault("budget", "0"));
            String type = f.getOrDefault("ad_type", "Digital").trim();
            String audience = f.getOrDefault("audience", "All").trim();
            int advertiserId = Integer.parseInt(f.getOrDefault("advertiser_id", "1"));
            int platformId = Integer.parseInt(f.getOrDefault("platform_id", "1"));
            int days = Integer.parseInt(f.getOrDefault("days", "30"));
            String status = f.getOrDefault("status", "Active").trim();

            WebAdRuntime.validate(title, budget, days);

            try (Connection con = DBConnection.getConnection()) {
                // Ensure foreign keys exist or fallback to first available
                try (PreparedStatement check = con.prepareStatement(
                    "SELECT (SELECT advertiser_id FROM advertiser WHERE advertiser_id = ?) AS adv, " +
                    "       (SELECT platform_id FROM platform WHERE platform_id = ?) AS plat, " +
                    "       (SELECT advertiser_id FROM advertiser LIMIT 1) AS fallback_adv, " +
                    "       (SELECT platform_id FROM platform LIMIT 1) AS fallback_plat")) {
                    check.setInt(1, advertiserId);
                    check.setInt(2, platformId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getObject("adv") == null) advertiserId = rs.getInt("fallback_adv");
                            if (rs.getObject("plat") == null) platformId = rs.getInt("fallback_plat");
                        }
                    }
                }

                String sql = "INSERT INTO advertisement "
                    + "(ad_id, title, description, budget, status, ad_type, audience, advertiser_id, platform_id, start_date, end_date) "
                    + "SELECT COALESCE(MAX(ad_id), 100) + 1, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, CURRENT_DATE + ? "
                    + "FROM advertisement RETURNING ad_id";

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, title);
                    ps.setString(2, desc);
                    ps.setDouble(3, budget);
                    ps.setString(4, status);
                    ps.setString(5, type);
                    ps.setString(6, audience);
                    ps.setInt(7, advertiserId);
                    ps.setInt(8, platformId);
                    ps.setInt(9, days);

                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        int newId = rs.getInt(1);
                        WebAdRuntime.createDomainAdvertisement(
                            newId, title, desc, budget, type, "", audience);
                        System.out.println("Inserted new advertisement in PostgreSQL: ID=" + newId + ", Title=" + title);
                        sendJson(ex, 200, "{\"success\":true,\"id\":" + newId + ",\"message\":\"Advertisement created successfully.\"}");
                        return;
                    }
                }
            }
            sendJson(ex, 500, "{\"error\":\"Failed to insert advertisement.\"}");

        } catch (InvalidAdvertisementException | NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":" + q(e.getMessage()) + "}");
        } catch (SQLException e) {
            System.err.println("SQL error in /api/ads POST: " + e.getMessage());
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        } catch (IOException e) {
            throw e;
        }
    }

    // ----------------------------------------------------
    // UPDATE Advertisement (fires trigger advertisement_status_update!)
    // ----------------------------------------------------
    static void updateAdHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        Map<String, String> f = parseParams(ex);
        try {
            int id = Integer.parseInt(f.get("id"));
            String title = f.getOrDefault("title", "").trim();
            String desc = f.getOrDefault("description", "").trim();
            double budget = Double.parseDouble(f.getOrDefault("budget", "0"));
            String status = f.getOrDefault("status", "Active").trim();
            String audience = f.getOrDefault("audience", "").trim();
            String type = f.getOrDefault("ad_type", "").trim();
            String daysStr = f.get("days");

            if (title.isEmpty() || budget <= 0) {
                throw new InvalidAdvertisementException("Title and positive budget are required.");
            }
            int days = daysStr == null || daysStr.isEmpty() ? 1 : Integer.parseInt(daysStr);
            WebAdRuntime.validate(title, budget, days);

            try (Connection con = DBConnection.getConnection()) {
                String sql;
                if (daysStr != null && !daysStr.isEmpty()) {
                    sql = "UPDATE advertisement SET title=?, description=?, budget=?, status=?, audience=?, " +
                          "ad_type=COALESCE(NULLIF(?,''), ad_type), end_date=CURRENT_DATE + ? WHERE ad_id=?";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setString(1, title);
                        ps.setString(2, desc);
                        ps.setDouble(3, budget);
                        ps.setString(4, status);
                        ps.setString(5, audience);
                        ps.setString(6, type);
                        ps.setInt(7, days);
                        ps.setInt(8, id);
                        int affected = ps.executeUpdate();
                        if (affected > 0) {
                            WebAdRuntime.remove(id);
                            sendJson(ex, 200, "{\"success\":true,\"message\":\"Advertisement updated. Trigger logged any status transition!\"}");
                            return;
                        }
                    }
                } else {
                    sql = "UPDATE advertisement SET title=?, description=?, budget=?, status=?, audience=?, " +
                          "ad_type=COALESCE(NULLIF(?,''), ad_type) WHERE ad_id=?";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setString(1, title);
                        ps.setString(2, desc);
                        ps.setDouble(3, budget);
                        ps.setString(4, status);
                        ps.setString(5, audience);
                        ps.setString(6, type);
                        ps.setInt(7, id);
                        int affected = ps.executeUpdate();
                        if (affected > 0) {
                            WebAdRuntime.remove(id);
                            sendJson(ex, 200, "{\"success\":true,\"message\":\"Advertisement updated. Trigger logged any status transition!\"}");
                            return;
                        }
                    }
                }
            }
            sendJson(ex, 404, "{\"error\":\"Ad not found with ID " + id + "\"}");

        } catch (InvalidAdvertisementException | NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":" + q(e.getMessage()) + "}");
        } catch (SQLException e) {
            System.err.println("SQL error in /api/ads/update: " + e.getMessage());
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // DELETE Advertisement
    // ----------------------------------------------------
    static void deleteAdHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        Map<String, String> f = parseParams(ex);
        try {
            int id = Integer.parseInt(f.get("id"));

            try (Connection con = DBConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    // Safe cascade deletion
                    try (PreparedStatement p1 = con.prepareStatement("DELETE FROM campaign_ad WHERE ad_id = ?")) {
                        p1.setInt(1, id);
                        p1.executeUpdate();
                    }
                    try (PreparedStatement p2 = con.prepareStatement("DELETE FROM payment WHERE ad_id = ?")) {
                        p2.setInt(1, id);
                        p2.executeUpdate();
                    }
                    try (PreparedStatement p3 = con.prepareStatement("DELETE FROM ad_update_log WHERE ad_id = ?")) {
                        p3.setInt(1, id);
                        p3.executeUpdate();
                    }
                    int affected;
                    try (PreparedStatement p4 = con.prepareStatement("DELETE FROM advertisement WHERE ad_id = ?")) {
                        p4.setInt(1, id);
                        affected = p4.executeUpdate();
                    }

                    con.commit();
                    if (affected > 0) {
                        WebAdRuntime.remove(id);
                        System.out.println("Deleted advertisement ID: " + id);
                        sendJson(ex, 200, "{\"success\":true,\"message\":\"Ad ID " + id + " permanently deleted.\"}");
                    } else {
                        sendJson(ex, 404, "{\"error\":\"Ad not found.\"}");
                    }
                } catch (SQLException e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            }
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":" + q(e.getMessage()) + "}");
        } catch (SQLException e) {
            System.err.println("SQL error in /api/ads/delete: " + e.getMessage());
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // PostgreSQL Function: expire_old_ads()
    // ----------------------------------------------------
    static void expireAdsHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT expire_old_ads()")) {
            int count = 0;
            if (rs.next()) {
                count = rs.getInt(1);
            }
            sendJson(ex, 200, "{\"success\":true,\"expired_count\":" + count + ",\"message\":\"PostgreSQL function expire_old_ads() executed. " + count + " ads expired.\"}");
        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // PostgreSQL Trigger Audit Log: ad_update_log
    // ----------------------------------------------------
    static void logsHandler(HttpExchange ex) throws IOException {
        String sql = "SELECT log_id, ad_id, old_status, new_status, changed_at FROM ad_update_log ORDER BY log_id DESC LIMIT 50";
        StringBuilder json = new StringBuilder("[");

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"log_id\":").append(rs.getLong("log_id"))
                    .append(",\"ad_id\":").append(rs.getInt("ad_id"))
                    .append(",\"old_status\":").append(q(rs.getString("old_status")))
                    .append(",\"new_status\":").append(q(rs.getString("new_status")))
                    .append(",\"changed_at\":").append(q(rs.getString("changed_at")))
                    .append("}");
            }
            json.append("]");
            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Aggregate Functions (COUNT, SUM, AVG, MIN, MAX)
    // ----------------------------------------------------
    static void statsHandler(HttpExchange ex) throws IOException {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {

            // Overall aggregates
            String sql1 = "SELECT COUNT(*) AS total_ads, " +
                          "       COALESCE(SUM(budget), 0) AS total_budget, " +
                          "       COALESCE(AVG(budget), 0) AS avg_budget, " +
                          "       COALESCE(MIN(budget), 0) AS min_budget, " +
                          "       COALESCE(MAX(budget), 0) AS max_budget " +
                          "FROM advertisement";

            StringBuilder json = new StringBuilder("{");
            try (ResultSet rs1 = st.executeQuery(sql1)) {
                if (rs1.next()) {
                    json.append("\"total_ads\":").append(rs1.getInt("total_ads"))
                        .append(",\"total_budget\":").append(rs1.getDouble("total_budget"))
                        .append(",\"avg_budget\":").append(rs1.getDouble("avg_budget"))
                        .append(",\"min_budget\":").append(rs1.getDouble("min_budget"))
                        .append(",\"max_budget\":").append(rs1.getDouble("max_budget"));
                }
            }

            // Group by ad_type
            String sql2 = "SELECT COALESCE(ad_type, 'Unspecified') AS ad_type, COUNT(*) AS count, COALESCE(SUM(budget), 0) AS budget " +
                          "FROM advertisement GROUP BY ad_type";
            StringBuilder typeArr = new StringBuilder("[");
            try (ResultSet rs2 = st.executeQuery(sql2)) {
                boolean first = true;
                while (rs2.next()) {
                    if (!first) typeArr.append(",");
                    first = false;
                    typeArr.append("{\"type\":").append(q(rs2.getString("ad_type")))
                           .append(",\"count\":").append(rs2.getInt("count"))
                           .append(",\"budget\":").append(rs2.getDouble("budget"))
                           .append("}");
                }
            }
            typeArr.append("]");

            // Group by status
            String sql3 = "SELECT status, COUNT(*) AS count FROM advertisement GROUP BY status";
            StringBuilder statusArr = new StringBuilder("[");
            try (ResultSet rs3 = st.executeQuery(sql3)) {
                boolean first = true;
                while (rs3.next()) {
                    if (!first) statusArr.append(",");
                    first = false;
                    statusArr.append("{\"status\":").append(q(rs3.getString("status")))
                             .append(",\"count\":").append(rs3.getInt("count"))
                             .append("}");
                }
            }
            statusArr.append("]");

            json.append(",\"by_type\":").append(typeArr)
                .append(",\"by_status\":").append(statusArr)
                .append("}");

            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Reports view: ad_report
    // ----------------------------------------------------
    static void reportsHandler(HttpExchange ex) throws IOException {
        String sql = "SELECT ad_id, title, advertiser, platform_name, budget, total_cost, status FROM ad_report ORDER BY ad_id";
        StringBuilder json = new StringBuilder("[");

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"ad_id\":").append(rs.getInt("ad_id"))
                    .append(",\"title\":").append(q(rs.getString("title")))
                    .append(",\"advertiser\":").append(q(rs.getString("advertiser")))
                    .append(",\"platform\":").append(q(rs.getString("platform_name")))
                    .append(",\"budget\":").append(rs.getDouble("budget"))
                    .append(",\"total_cost\":").append(rs.getDouble("total_cost"))
                    .append(",\"status\":").append(q(rs.getString("status")))
                    .append("}");
            }
            json.append("]");
            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Transactional Payment: BEGIN / COMMIT / ROLLBACK
    // ----------------------------------------------------
    static void paymentsHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        Map<String, String> f = parseParams(ex);
        try {
            int adId = Integer.parseInt(f.get("ad_id"));
            double amount = Double.parseDouble(f.get("amount"));
            String method = f.getOrDefault("method", "UPI").trim();

            try (Connection con = DBConnection.getConnection()) {
                con.setAutoCommit(false); // BEGIN transaction
                try {
                    String getNextId = "SELECT COALESCE(MAX(payment_id), 0) + 1 FROM payment";
                    int nextPaymentId = 1;
                    try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(getNextId)) {
                        if (rs.next()) nextPaymentId = rs.getInt(1);
                    }

                    String sql = "INSERT INTO payment (payment_id, ad_id, amount, payment_method, status) VALUES (?, ?, ?, ?, 'Paid')";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setInt(1, nextPaymentId);
                        ps.setInt(2, adId);
                        ps.setDouble(3, amount);
                        ps.setString(4, method);
                        ps.executeUpdate();
                    }

                    con.commit(); // COMMIT
                    System.out.println("Committed payment ID " + nextPaymentId + " for Ad " + adId + " amount ₹" + amount);
                    sendJson(ex, 200, "{\"success\":true,\"payment_id\":" + nextPaymentId + ",\"message\":\"Payment of ₹" + amount + " saved via PostgreSQL transaction!\"}");

                } catch (SQLException e) {
                    con.rollback(); // ROLLBACK
                    System.err.println("Rolled back payment: " + e.getMessage());
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 400, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // POST /api/advertisers
    // ----------------------------------------------------
    static void advertisersHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Only POST is supported for advertisers.\"}");
            return;
        }

        Map<String, String> f = parseParams(ex);
        String name = f.getOrDefault("name", "").trim();
        String email = f.getOrDefault("email", "").trim();
        String phone = f.getOrDefault("phone", "").trim();

        if (name.isEmpty()) {
            sendJson(ex, 400, "{\"error\":\"Advertiser name is required.\"}");
            return;
        }
        if (name.length() > 50 || email.length() > 80 || phone.length() > 15) {
            sendJson(ex, 400, "{\"error\":\"Advertiser details exceed the database field limits.\"}");
            return;
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO advertiser (advertiser_id, name, email, phone) " +
                 "SELECT COALESCE(MAX(advertiser_id), 0) + 1, ?, NULLIF(?, ''), NULLIF(?, '') " +
                 "FROM advertiser RETURNING advertiser_id")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("advertiser_id");
                    sendJson(ex, 200, "{\"success\":true,\"id\":" + id +
                        ",\"message\":\"Advertiser created successfully.\"}");
                    return;
                }
            }
            sendJson(ex, 500, "{\"error\":\"Database did not return the new advertiser ID.\"}");
        } catch (SQLException e) {
            System.err.println("SQL error in /api/advertisers POST: " + e.getMessage());
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Meta (Advertisers & Platforms)
    // ----------------------------------------------------
    static void metaHandler(HttpExchange ex) throws IOException {
        StringBuilder json = new StringBuilder("{\"advertisers\":[");

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {

            try (ResultSet rs = st.executeQuery("SELECT advertiser_id, name, email FROM advertiser ORDER BY advertiser_id")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"id\":").append(rs.getInt("advertiser_id"))
                        .append(",\"name\":").append(q(rs.getString("name")))
                        .append(",\"email\":").append(q(rs.getString("email")))
                        .append("}");
                }
            }
            json.append("],\"platforms\":[");

            try (ResultSet rs = st.executeQuery("SELECT platform_id, platform_name FROM platform ORDER BY platform_id")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"id\":").append(rs.getInt("platform_id"))
                        .append(",\"name\":").append(q(rs.getString("platform_name")))
                        .append("}");
                }
            }
            json.append("]}");

            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Campaigns Management (List, Insert, Delete, Details)
    // ----------------------------------------------------
    static void campaignsHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> f = parseParams(ex);
            String name = f.getOrDefault("name", "").trim();
            String adIds = f.getOrDefault("ad_ids", "").trim();

            if (name.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"Campaign name is required\"}");
                return;
            }

            try (Connection con = DBConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    String sql = "INSERT INTO campaign (campaign_id, name) " +
                                 "SELECT COALESCE(MAX(campaign_id), 0) + 1, ? FROM campaign RETURNING campaign_id";
                    int campaignId;
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setString(1, name);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) {
                            con.rollback();
                            sendJson(ex, 500, "{\"error\":\"Could not create campaign\"}");
                            return;
                        }
                        campaignId = rs.getInt(1);
                    }

                    // Link advertisements if selected
                    if (!adIds.isEmpty()) {
                        String[] parts = adIds.split("[,; ]+");
                        String linkSql = "INSERT INTO campaign_ad (campaign_id, ad_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
                        try (PreparedStatement psLink = con.prepareStatement(linkSql)) {
                            for (String p : parts) {
                                try {
                                    int adId = Integer.parseInt(p.trim());
                                    psLink.setInt(1, campaignId);
                                    psLink.setInt(2, adId);
                                    psLink.executeUpdate();
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }

                    con.commit();
                    sendJson(ex, 200, "{\"success\":true,\"id\":" + campaignId + ",\"name\":" + q(name) + ",\"message\":\"Campaign created successfully.\"}");
                    return;
                } catch (SQLException e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
                return;
            }
        }

        // GET /api/campaigns: returns campaigns with connected ad counts and budgets
        String sql = "SELECT c.campaign_id, c.name, " +
                     "       COUNT(ca.ad_id) AS ad_count, " +
                     "       COALESCE(SUM(a.budget), 0) AS total_budget, " +
                     "       COALESCE(STRING_AGG(DISTINCT a.ad_type, ', '), 'Digital') AS ad_types, " +
                     "       COALESCE(STRING_AGG(DISTINCT p.platform_name, ', '), 'Multi-channel') AS platforms " +
                     "FROM campaign c " +
                     "LEFT JOIN campaign_ad ca ON c.campaign_id = ca.campaign_id " +
                     "LEFT JOIN advertisement a ON ca.ad_id = a.ad_id " +
                     "LEFT JOIN platform p ON a.platform_id = p.platform_id " +
                     "GROUP BY c.campaign_id, c.name " +
                     "ORDER BY c.campaign_id ASC";

        StringBuilder json = new StringBuilder("[");
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"id\":").append(rs.getInt("campaign_id"))
                    .append(",\"name\":").append(q(rs.getString("name")))
                    .append(",\"ad_count\":").append(rs.getInt("ad_count"))
                    .append(",\"total_budget\":").append(rs.getDouble("total_budget"))
                    .append(",\"ad_types\":").append(q(rs.getString("ad_types")))
                    .append(",\"platforms\":").append(q(rs.getString("platforms")))
                    .append("}");
            }
            json.append("]");
            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // GET /api/campaigns/details?id=...
    static void campaignDetailsHandler(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(ex);
        int campaignId;
        try {
            campaignId = Integer.parseInt(params.getOrDefault("id", "0"));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Invalid campaign id\"}");
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            String name = "";
            String campSql = "SELECT name FROM campaign WHERE campaign_id = ?";
            try (PreparedStatement ps = con.prepareStatement(campSql)) {
                ps.setInt(1, campaignId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    name = rs.getString("name");
                } else {
                    sendJson(ex, 404, "{\"error\":\"Campaign not found\"}");
                    return;
                }
            }

            String adsSql = "SELECT a.ad_id, a.title, a.budget, a.status, a.ad_type, a.audience, " +
                            "       COALESCE(r.name, 'Unknown') AS advertiser_name, " +
                            "       COALESCE(p.platform_name, 'Unknown') AS platform_name, " +
                            "       a.end_date, " +
                            "       CASE WHEN a.end_date IS NULL THEN NULL ELSE GREATEST(a.end_date - CURRENT_DATE, 0) END AS days_left " +
                            "FROM campaign_ad ca " +
                            "JOIN advertisement a ON ca.ad_id = a.ad_id " +
                            "LEFT JOIN advertiser r ON a.advertiser_id = r.advertiser_id " +
                            "LEFT JOIN platform p ON a.platform_id = p.platform_id " +
                            "WHERE ca.campaign_id = ? " +
                            "ORDER BY a.ad_id ASC";

            StringBuilder json = new StringBuilder("{");
            json.append("\"id\":").append(campaignId)
                .append(",\"name\":").append(q(name))
                .append(",\"ads\":[");

            double totalBudget = 0;
            try (PreparedStatement psAds = con.prepareStatement(adsSql)) {
                psAds.setInt(1, campaignId);
                ResultSet rsAds = psAds.executeQuery();
                boolean first = true;
                while (rsAds.next()) {
                    if (!first) json.append(",");
                    first = false;
                    double b = rsAds.getDouble("budget");
                    totalBudget += b;
                    String days = rsAds.getString("days_left");
                    json.append("{")
                        .append("\"id\":").append(rsAds.getInt("ad_id"))
                        .append(",\"title\":").append(q(rsAds.getString("title")))
                        .append(",\"budget\":").append(b)
                        .append(",\"status\":").append(q(rsAds.getString("status")))
                        .append(",\"ad_type\":").append(q(rsAds.getString("ad_type")))
                        .append(",\"audience\":").append(q(rsAds.getString("audience")))
                        .append(",\"advertiser_name\":").append(q(rsAds.getString("advertiser_name")))
                        .append(",\"platform_name\":").append(q(rsAds.getString("platform_name")))
                        .append(",\"days\":").append(days == null ? "null" : days)
                        .append("}");
                }
            }
            json.append("],\"total_budget\":").append(totalBudget).append("}");
            sendJson(ex, 200, json.toString());

        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // POST /api/campaigns/delete
    static void campaignDeleteHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }

        Map<String, String> params = parseParams(ex);
        int campaignId;
        try {
            campaignId = Integer.parseInt(params.getOrDefault("id", "0"));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Invalid campaign id\"}");
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement p1 = con.prepareStatement("DELETE FROM campaign_ad WHERE campaign_id = ?")) {
                    p1.setInt(1, campaignId);
                    p1.executeUpdate();
                }
                int aff;
                try (PreparedStatement p2 = con.prepareStatement("DELETE FROM campaign WHERE campaign_id = ?")) {
                    p2.setInt(1, campaignId);
                    aff = p2.executeUpdate();
                }
                con.commit();
                if (aff > 0) {
                    sendJson(ex, 200, "{\"success\":true,\"message\":\"Campaign deleted successfully.\"}");
                } else {
                    sendJson(ex, 404, "{\"error\":\"Campaign not found\"}");
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            sendJson(ex, 500, "{\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Database connection status & connect
    // ----------------------------------------------------
    static void dbStatusHandler(HttpExchange ex) throws IOException {
        try (Connection con = DBConnection.getConnection()) {
            boolean valid = con.isValid(2);
            String dbName = con.getCatalog();
            sendJson(ex, 200, "{\"connected\":true,\"database\":" + q(dbName) + ",\"user\":" + q(DBConnection.USER) + ",\"url\":" + q(DBConnection.URL) + "}");
        } catch (SQLException e) {
            sendJson(ex, 200, "{\"connected\":false,\"error\":" + q(e.getMessage()) + ",\"user\":" + q(DBConnection.USER) + ",\"url\":" + q(DBConnection.URL) + "}");
        }
    }

    static void dbConnectHandler(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 204, "{}");
            return;
        }
        Map<String, String> f = parseParams(ex);
        String url = f.get("url");
        String user = f.get("user");
        String pass = f.get("password");

        DBConnection.setCredentials(url, user, pass);

        try (Connection con = DBConnection.getConnection()) {
            boolean valid = con.isValid(2);
            String dbName = con.getCatalog();
            sendJson(ex, 200, "{\"success\":true,\"connected\":true,\"database\":" + q(dbName) + ",\"message\":\"Successfully connected to PostgreSQL!\"}");
        } catch (SQLException e) {
            sendJson(ex, 400, "{\"success\":false,\"connected\":false,\"error\":" + q(e.getMessage()) + "}");
        }
    }

    // ----------------------------------------------------
    // Static Files Handler
    // ----------------------------------------------------
    static void filesHandler(HttpExchange ex) throws IOException {
        String pathStr = ex.getRequestURI().getPath();
        if (pathStr.equals("/") || pathStr.isEmpty()) pathStr = "/index.html";

        Path filePath = Paths.get(pathStr.startsWith("/") ? pathStr.substring(1) : pathStr);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendText(ex, 404, "File not found: " + pathStr);
            return;
        }

        String mime = "text/plain";
        if (pathStr.endsWith(".html"))      mime = "text/html; charset=utf-8";
        else if (pathStr.endsWith(".css"))  mime = "text/css; charset=utf-8";
        else if (pathStr.endsWith(".js"))   mime = "text/javascript; charset=utf-8";
        else if (pathStr.endsWith(".json")) mime = "application/json; charset=utf-8";
        else if (pathStr.endsWith(".svg"))  mime = "image/svg+xml";
        else if (pathStr.endsWith(".png"))  mime = "image/png";

        byte[] data = Files.readAllBytes(filePath);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}
