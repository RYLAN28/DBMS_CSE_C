public abstract class Advertisement {
    private int adId;
    private String title;
    private String description;
    private double budget;
    private String status;

    static int totalAds = 0;
    final double TAX = 0.18;

    public Advertisement(int adId, String title, String description, double budget) {
        this.adId = adId;
        this.title = title;
        this.description = description;
        this.budget = budget;
        this.status = "Active";
        totalAds++;
    }

    public int getAdId() { return adId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public double getBudget() { return budget; }
    public String getStatus() { return status; }

    public void setStatus(String status) {
        this.status = status;
    }

    public void display() {
        System.out.println("Ad ID: " + adId);
        System.out.println("Title: " + title);
        System.out.println("Description: " + description);
        System.out.println("Budget: Rs. " + budget);
        System.out.println("Status: " + status);
        System.out.println("Category: " + getCategory());
    }

    public abstract String getCategory();

    public double getTotalCost() {
        return budget + (budget * TAX);
    }

    public static void showTotalAds() {
        System.out.println("Total Ads: " + totalAds);
    }
}
