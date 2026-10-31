import java.sql.*;

public class DBFeatures {

    // 1. show the dashboard view (end date, payment status, last update)
    public static void showDashboard() {
        String sql = "SELECT ad_id, title, status, show_status, days_left, payment_status, updated_at "
                   + "FROM ad_dashboard ORDER BY ad_id";

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- AD DASHBOARD ---");
            while (rs.next()) {
                System.out.println(rs.getString("ad_id") + " | "
                    + rs.getString("title") + " | "
                    + rs.getString("status") + " | "
                    + rs.getString("show_status") + " | days left: "
                    + rs.getString("days_left") + " | "
                    + rs.getString("payment_status") + " | updated: "
                    + rs.getString("updated_at"));
            }
        } catch (SQLException e) {
            System.out.println("Could not show dashboard: " + e.getMessage());
        }
    }

    // 2. add a payment using a transaction (commit / rollback)
    public static void makePayment(int paymentId, int adId, double amount, String method) {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);               // start transaction (BEGIN)

            String sql = "INSERT INTO payment (payment_id, ad_id, amount, payment_method) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, paymentId);
            ps.setInt(2, adId);
            ps.setDouble(3, amount);
            ps.setString(4, method);
            ps.executeUpdate();

            con.commit();                           // save (COMMIT)
            System.out.println("Payment saved for ad " + adId);

        } catch (SQLException e) {
            System.out.println("Payment failed: " + e.getMessage());
            try {
                if (con != null) con.rollback();    // undo (ROLLBACK)
                System.out.println("Changes rolled back.");
            } catch (SQLException ex) {
                System.out.println("Rollback error: " + ex.getMessage());
            }
        } finally {
            try {
                if (con != null) con.close();       // always close
            } catch (SQLException e) {
                System.out.println("Close error: " + e.getMessage());
            }
        }
    }

    // 3. change the end date of an ad (the trigger records the update)
    public static void setEndDate(int adId, String date) {   // date like "2026-12-31"
        String sql = "UPDATE advertisement SET end_date = ? WHERE ad_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));
            ps.setInt(2, adId);
            int rows = ps.executeUpdate();

            if (rows == 0) System.out.println("Ad " + adId + " not found.");
            else           System.out.println("End date updated for ad " + adId);

        } catch (IllegalArgumentException e) {
            System.out.println("Wrong date format. Use yyyy-mm-dd.");
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // 4. call the SQL function that expires old ads
    public static void expireOldAds() {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT expire_old_ads()")) {

            if (rs.next()) System.out.println("Ads expired: " + rs.getInt(1));

        } catch (SQLException e) {
            System.out.println("Expire failed: " + e.getMessage());
        }
    }

    // 5. show the update log (filled by the trigger)
    public static void showLog() {
        String sql = "SELECT ad_id, old_status, new_status, changed_at FROM ad_update_log ORDER BY log_id";

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- UPDATE LOG ---");
            while (rs.next()) {
                System.out.println("Ad " + rs.getInt("ad_id") + ": "
                    + rs.getString("old_status") + " -> "
                    + rs.getString("new_status") + " at "
                    + rs.getString("changed_at"));
            }
        } catch (SQLException e) {
            System.out.println("Could not show log: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        showDashboard();
        makePayment(11, 103, 13750, "UPI");   // run twice to see the error handling (duplicate payment_id)
        setEndDate(102, "2027-01-31");
        expireOldAds();
        showDashboard();
        showLog();
    }
}
