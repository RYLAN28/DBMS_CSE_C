import java.sql.*;

public class DBConnection {
    // defaults matching pgAdmin setup
    public static String URL = System.getenv().getOrDefault("ADWISE_DB_URL", "jdbc:postgresql://localhost:5432/AdvertisingManagementSystem");
    public static String USER = System.getenv().getOrDefault("ADWISE_DB_USER", "postgres");
    public static String PASS = System.getenv().getOrDefault("ADWISE_DB_PASSWORD", "");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static synchronized void setCredentials(String url, String user, String pass) {
        if (url != null && !url.trim().isEmpty()) URL = url.trim();
        if (user != null && !user.trim().isEmpty()) USER = user.trim();
        if (pass != null) PASS = pass;
    }

    // quick test: run this file on its own
    public static void main(String[] args) {
        try (Connection con = getConnection()) {
            System.out.println("Connected to PostgreSQL!");

            // INSERT using your existing Java values
            String insert = "INSERT INTO advertisement "
                + "(ad_id, title, description, budget, ad_type, advertiser_id, platform_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (ad_id) DO NOTHING";
            try (PreparedStatement ps = con.prepareStatement(insert)) {
                ps.setInt(1, 201);
                ps.setString(2, "Java Added Ad");
                ps.setString(3, "Inserted from Java");
                ps.setDouble(4, 15000);
                ps.setString(5, "Digital");
                ps.setInt(6, 1);
                ps.setInt(7, 1);
                ps.executeUpdate();
            }

            // SELECT with JOIN
            String sql = "SELECT a.ad_id, a.title, r.name, a.budget "
                + "FROM advertisement a JOIN advertiser r ON a.advertiser_id = r.advertiser_id";
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.println(rs.getInt("ad_id") + " | " + rs.getString("title")
                        + " | " + rs.getString("name") + " | Rs. " + rs.getDouble("budget"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
