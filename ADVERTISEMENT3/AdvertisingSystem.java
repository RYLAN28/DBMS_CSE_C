import java.sql.*;

// Same menu methods as before, but the data now lives in PostgreSQL
public class AdvertisingSystem {

    // ---------- small helpers ----------

    // run a query that returns one value (like COUNT or SUM)
    private String one(String sql) {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "0";
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return "0";
        }
    }

    // find the platform id by name, or create the platform if it is new
    private int platformId(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT platform_id FROM platform WHERE LOWER(platform_name) = LOWER(?)")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO platform (platform_id, platform_name) "
              + "SELECT COALESCE(MAX(platform_id), 0) + 1, ? FROM platform RETURNING platform_id")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    // ---------- ADD ----------

    public void addAdvertisement(Advertisement ad, int advertiserId) {
        String sql = "INSERT INTO advertisement "
            + "(ad_id, title, description, budget, status, ad_type, audience, advertiser_id, platform_id, end_date) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE + 30)";

        try (Connection con = DBConnection.getConnection()) {
            String type = "General";
            String audience = null;
            Integer pid = null;

            if (ad instanceof DigitalAdvertisement) {
                DigitalAdvertisement d = (DigitalAdvertisement) ad;
                type = "Digital";
                audience = d.getAudience();
                pid = platformId(con, d.getPlatform());
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, ad.getAdId());
                ps.setString(2, ad.getTitle());
                ps.setString(3, ad.getDescription());
                ps.setDouble(4, ad.getBudget());
                ps.setString(5, ad.getStatus());
                ps.setString(6, type);
                ps.setString(7, audience);
                ps.setInt(8, advertiserId);
                if (pid == null) ps.setNull(9, Types.INTEGER);
                else             ps.setInt(9, pid);
                ps.executeUpdate();
            }
            System.out.println("Advertisement added successfully.");

        } catch (SQLException e) {
            System.out.println("Could not add advertisement: " + e.getMessage());
        }
    }

    public void addAdvertiser(Advertiser a) {
        String sql = "INSERT INTO advertiser (advertiser_id, name, email, phone) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, a.getAdvertiserId());
            ps.setString(2, a.getName());
            ps.setString(3, a.getEmail());
            ps.setString(4, a.getPhone());
            ps.executeUpdate();
            System.out.println("Advertiser added successfully.");
        } catch (SQLException e) {
            System.out.println("Could not add advertiser: " + e.getMessage());
        }
    }

    // campaign + its ads are saved together in one transaction
    public void addCampaign(Campaign c) {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO campaign (campaign_id, name) VALUES (?, ?)")) {
                ps.setInt(1, c.getCampaignId());
                ps.setString(2, c.getName());
                ps.executeUpdate();
            }
            for (int adId : c.getAdIds()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO campaign_ad (campaign_id, ad_id) VALUES (?, ?)")) {
                    ps.setInt(1, c.getCampaignId());
                    ps.setInt(2, adId);
                    ps.executeUpdate();
                }
            }
            con.commit();
            System.out.println("Campaign added successfully.");

        } catch (SQLException e) {
            System.out.println("Could not add campaign: " + e.getMessage());
            try {
                if (con != null) con.rollback();
            } catch (SQLException ex) {
                System.out.println("Rollback error: " + ex.getMessage());
            }
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException ex) {
                System.out.println("Close error: " + ex.getMessage());
            }
        }
    }

    public void addPayment(Payment p) {
        String sql = "INSERT INTO payment (payment_id, ad_id, amount, payment_method) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, p.getPaymentId());
            ps.setInt(2, p.getAdId());
            ps.setDouble(3, p.getAmount());
            ps.setString(4, p.getMethod());
            ps.executeUpdate();
            System.out.println("Payment added successfully.");
        } catch (SQLException e) {
            System.out.println("Could not add payment: " + e.getMessage());
        }
    }

    public void addPlatform(String platform) {
        try (Connection con = DBConnection.getConnection()) {
            platformId(con, platform);
            System.out.println("Platform added.");
        } catch (SQLException e) {
            System.out.println("Could not add platform: " + e.getMessage());
        }
    }

    // ---------- VIEW / SEARCH ----------

    // prints ads (all of them, or only the one with this id); returns how many were printed
    private int showAds(Integer id) {
        String sql = "SELECT a.ad_id, a.title, a.description, a.budget, a.status, a.ad_type, "
            + "p.platform_name, a.audience, a.end_date "
            + "FROM advertisement a LEFT JOIN platform p ON a.platform_id = p.platform_id "
            + (id == null ? "" : "WHERE a.ad_id = ? ")
            + "ORDER BY a.ad_id";
        int count = 0;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (id != null) ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                count++;
                double budget = rs.getDouble("budget");
                String end = rs.getString("end_date");
                System.out.println();
                System.out.println("Ad ID: " + rs.getInt("ad_id"));
                System.out.println("Title: " + rs.getString("title"));
                System.out.println("Description: " + rs.getString("description"));
                System.out.println("Budget: Rs. " + budget);
                System.out.println("Status: " + rs.getString("status"));
                System.out.println("Type: " + rs.getString("ad_type"));
                System.out.println("Platform: " + rs.getString("platform_name"));
                System.out.println("Audience: " + rs.getString("audience"));
                System.out.println("Ends on: " + (end == null ? "-" : end));
                System.out.println("Total Cost: Rs. " + (budget + budget * 0.18));
                System.out.println("--------------------");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
        return count;
    }

    public void displayAds() {
        System.out.println("\n===== Advertisements =====");
        if (showAds(null) == 0) System.out.println("No advertisements.");
    }

    public void searchAd(int id) {
        if (showAds(id) == 0) System.out.println("Advertisement not found.");
    }

    public void displayAdvertisers() {
        System.out.println("\n===== Advertisers =====");
        String sql = "SELECT advertiser_id, name, email, phone FROM advertiser ORDER BY advertiser_id";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("Advertiser ID: " + rs.getInt("advertiser_id"));
                System.out.println("Name: " + rs.getString("name"));
                System.out.println("Email: " + rs.getString("email"));
                System.out.println("Phone: " + rs.getString("phone"));
                System.out.println("--------------------");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void searchAdvertiser(int id) {
        String sql = "SELECT advertiser_id, name, email, phone FROM advertiser WHERE advertiser_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("Advertiser ID: " + rs.getInt("advertiser_id"));
                System.out.println("Name: " + rs.getString("name"));
                System.out.println("Email: " + rs.getString("email"));
                System.out.println("Phone: " + rs.getString("phone"));
            } else {
                System.out.println("Advertiser not found.");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void displayCampaigns() {
        System.out.println("\n===== Campaigns =====");
        String sql = "SELECT c.campaign_id, c.name, ca.ad_id "
            + "FROM campaign c LEFT JOIN campaign_ad ca ON c.campaign_id = ca.campaign_id "
            + "ORDER BY c.campaign_id, ca.ad_id";
        int lastId = -1;

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("campaign_id");
                if (id != lastId) {
                    if (lastId != -1) System.out.println("--------------------");
                    System.out.println("Campaign ID: " + id);
                    System.out.println("Campaign Name: " + rs.getString("name"));
                    System.out.println("Advertisement IDs:");
                    lastId = id;
                }
                int adId = rs.getInt("ad_id");
                if (!rs.wasNull()) System.out.println(adId);
            }
            if (lastId != -1) System.out.println("--------------------");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void displayPayments() {
        System.out.println("\n===== Payments =====");
        String sql = "SELECT payment_id, ad_id, amount, payment_method, status FROM payment ORDER BY payment_id";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("Payment ID: " + rs.getInt("payment_id"));
                System.out.println("Advertisement ID: " + rs.getInt("ad_id"));
                System.out.println("Amount: Rs. " + rs.getDouble("amount"));
                System.out.println("Method: " + rs.getString("payment_method"));
                System.out.println("Status: " + rs.getString("status"));
                System.out.println("--------------------");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public void displayPlatforms() {
        System.out.println("\n===== Platforms =====");
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT platform_name FROM platform ORDER BY platform_id")) {
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    // ---------- DELETE / UPDATE ----------

    // delete the ad together with its payments and campaign links (one transaction)
    public void deleteAd(int id) {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String[] sqls = {
                "DELETE FROM campaign_ad WHERE ad_id = ?",
                "DELETE FROM payment WHERE ad_id = ?",
                "DELETE FROM advertisement WHERE ad_id = ?"
            };
            int deleted = 0;
            for (String sql : sqls) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    deleted = ps.executeUpdate();     // the last one counts the ad itself
                }
            }

            if (deleted == 0) {
                con.rollback();
                System.out.println("Advertisement not found.");
            } else {
                con.commit();
                System.out.println("Advertisement deleted.");
            }
        } catch (SQLException e) {
            System.out.println("Could not delete: " + e.getMessage());
            try {
                if (con != null) con.rollback();
            } catch (SQLException ex) {
                System.out.println("Rollback error: " + ex.getMessage());
            }
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException ex) {
                System.out.println("Close error: " + ex.getMessage());
            }
        }
    }

    public void updateStatus(int id, String status) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE advertisement SET status = ? WHERE ad_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            System.out.println(rows == 0 ? "Advertisement not found." : "Status updated.");
        } catch (SQLException e) {
            System.out.println("Could not update: " + e.getMessage());
        }
    }

    // ---------- TOTALS / REPORT ----------

    public void showTotalBudget() {
        System.out.println("Total Advertisement Budget: Rs. "
            + one("SELECT COALESCE(SUM(budget), 0) FROM advertisement"));
    }

    public void showTotalAds() {
        System.out.println("Total Ads: " + one("SELECT COUNT(*) FROM advertisement"));
    }

    public void showReport() {
        System.out.println("\n========== REPORT ==========");
        System.out.println("Total Advertisements: " + one("SELECT COUNT(*) FROM advertisement"));
        System.out.println("Total Advertisers: " + one("SELECT COUNT(*) FROM advertiser"));
        System.out.println("Total Campaigns: " + one("SELECT COUNT(*) FROM campaign"));
        System.out.println("Total Payments: " + one("SELECT COUNT(*) FROM payment"));
        System.out.println("Total Platforms: " + one("SELECT COUNT(*) FROM platform"));
        showTotalBudget();
        System.out.println("============================");
    }

    public static class Report {
        public void message() {
            System.out.println("Advertising report generated.");
        }
    }
}
