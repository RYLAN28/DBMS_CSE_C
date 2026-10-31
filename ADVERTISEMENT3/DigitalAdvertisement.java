public class DigitalAdvertisement extends Advertisement {
    private String platform;
    private String audience;

    public DigitalAdvertisement(int adId, String title, String description,
                                double budget, String platform, String audience) {
        super(adId, title, description, budget);
        this.platform = platform;
        this.audience = audience;
    }

    public String getPlatform() { return platform; }
    public String getAudience() { return audience; }
    @Override
    public String getCategory() { return "Digital"; }

    @Override
    public void display() {
        super.display();
        System.out.println("Platform: " + platform);
        System.out.println("Audience: " + audience);
        System.out.println("Total Cost: Rs. " + getTotalCost());
    }
}
