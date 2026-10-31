import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class WebAdRuntime {
    private static final Map<Integer, Advertisement> CACHE =
        Collections.synchronizedMap(new HashMap<>());
    private static final ArrayList<Advertisement> ORDERED_ADS = new ArrayList<>();
    private static final Vector<String> EVENTS = new Vector<>();
    private static final ScheduledExecutorService EXPIRY_THREAD =
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ad-expiry-checker");
            thread.setDaemon(true);
            return thread;
        });

    private WebAdRuntime() {
    }

    public static void start() {
        EXPIRY_THREAD.scheduleWithFixedDelay(WebAdRuntime::expireAds,
            5, 5, TimeUnit.MINUTES);
    }

    public static void stop() {
        EXPIRY_THREAD.shutdownNow();
    }

    public static void validate(String title, double budget, int days)
            throws InvalidAdvertisementException {
        if (title == null || title.trim().isEmpty()) {
            throw new InvalidAdvertisementException("Title is required.");
        }
        if (budget <= 0) {
            throw new InvalidAdvertisementException("Budget must be greater than zero.");
        }
        if (days <= 0 || days > 3650) {
            throw new InvalidAdvertisementException("Duration must be between 1 and 3650 days.");
        }
    }

    public static Advertisement createDomainAdvertisement(
            int id, String title, String description, double budget,
            String type, String platform, String audience)
            throws InvalidAdvertisementException {
        validate(title, budget, 1);
        String safeType = type == null ? "Digital" : type.trim();
        String safePlatform = platform == null ? "" : platform.trim();
        String safeAudience = audience == null ? "All" : audience.trim();

        Advertisement ad;
        if ("TV".equalsIgnoreCase(safeType)) {
            ad = new TVAdvertisement(id, title, description, budget, 0, safePlatform);
        } else if ("Newspaper".equalsIgnoreCase(safeType)) {
            ad = new NewspaperAdvertisement(id, title, description, budget, safePlatform, 1);
        } else {
            ad = new DigitalAdvertisement(id, title, description, budget,
                safePlatform, safeAudience);
        }
        synchronized (ORDERED_ADS) {
            ORDERED_ADS.removeIf(existing -> existing.getAdId() == id);
            ORDERED_ADS.add(ad);
        }
        CACHE.put(id, ad);
        EVENTS.add("Loaded " + ad.getCategory() + " advertisement #" + id);
        return ad;
    }

    public static void cacheRow(ResultSet rs) throws SQLException {
        try {
            Advertisement ad = createDomainAdvertisement(
                rs.getInt("ad_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getDouble("budget"),
                rs.getString("ad_type"),
                rs.getString("platform_name"),
                rs.getString("audience"));
            ad.setStatus(rs.getString("status"));
        } catch (InvalidAdvertisementException e) {
            throw new SQLException("Invalid advertisement row: " + e.getMessage(), e);
        }
    }

    public static void remove(int id) {
        CACHE.remove(id);
        synchronized (ORDERED_ADS) {
            ORDERED_ADS.removeIf(ad -> ad.getAdId() == id);
        }
        EVENTS.add("Removed advertisement #" + id);
    }

    public static int cachedCount() {
        return CACHE.size();
    }

    public static int orderedCount() {
        synchronized (ORDERED_ADS) {
            return ORDERED_ADS.size();
        }
    }

    public static String latestEvent() {
        return EVENTS.isEmpty() ? "No runtime events yet." : EVENTS.lastElement();
    }

    private static void expireAds() {
        try (Connection con = DBConnection.getConnection();
             Statement statement = con.createStatement();
             ResultSet result = statement.executeQuery("SELECT expire_old_ads()")) {
            if (result.next()) {
                System.out.println("[ad-expiry-checker] Expired ads: " + result.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("[ad-expiry-checker] Database check failed: " + e.getMessage());
        }
    }
}
