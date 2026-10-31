public class TVAdvertisement extends Advertisement {
    private int seconds;
    private String channel;

    public TVAdvertisement(int adId, String title, String description,
                           double budget, int seconds, String channel) {
        super(adId, title, description, budget);
        this.seconds = seconds;
        this.channel = channel;
    }

    @Override
    public String getCategory() { return "TV"; }

    @Override
    public void display() {
        super.display();
        System.out.println("Channel: " + channel);
        System.out.println("Duration: " + seconds + " seconds");
        System.out.println("Total Cost: Rs. " + getTotalCost());
    }
}
