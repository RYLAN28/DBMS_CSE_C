public class Advertiser {
    private int advertiserId;
    private String name;
    private String email;
    private String phone;

    public Advertiser(int advertiserId, String name, String email, String phone) {
        this.advertiserId = advertiserId;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public int getAdvertiserId() { return advertiserId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public void display() {
        System.out.println("Advertiser ID: " + advertiserId);
        System.out.println("Name: " + name);
        System.out.println("Email: " + email);
        System.out.println("Phone: " + phone);
    }
}
