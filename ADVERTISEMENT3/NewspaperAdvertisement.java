public class NewspaperAdvertisement extends Advertisement {
    private String newspaper;
    private int pages;

    public NewspaperAdvertisement(int adId, String title, String description,
                                  double budget, String newspaper, int pages) {
        super(adId, title, description, budget);
        this.newspaper = newspaper;
        this.pages = pages;
    }

    @Override
    public String getCategory() { return "Newspaper"; }

    @Override
    public void display() {
        super.display();
        System.out.println("Newspaper: " + newspaper);
        System.out.println("Pages: " + pages);
        System.out.println("Total Cost: Rs. " + getTotalCost());
    }
}
