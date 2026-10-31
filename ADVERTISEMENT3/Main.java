import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        AdvertisingSystem system = new AdvertisingSystem();
        int choice;

        do {
            System.out.println("\n================================");
            System.out.println("   ADVERTISING MANAGEMENT SYSTEM");
            System.out.println("================================");

            System.out.println("1. Add Advertiser");
            System.out.println("2. Add Advertisement");
            System.out.println("3. Add Campaign");
            System.out.println("4. Add Payment");
            System.out.println("5. Add Platform");
            System.out.println("6. View Advertisements");
            System.out.println("7. View Advertisers");
            System.out.println("8. View Campaigns");
            System.out.println("9. View Payments");
            System.out.println("10. View Platforms");
            System.out.println("11. Search Advertisement");
            System.out.println("12. Search Advertiser");
            System.out.println("13. Delete Advertisement");
            System.out.println("14. Update Advertisement Status");
            System.out.println("15. Show Budget");
            System.out.println("16. Show Report");
            System.out.println("17. Show Total Ads");
            System.out.println("18. Exit");

            System.out.print("Enter choice: ");
            choice = sc.nextInt();

            switch (choice) {
                case 1:
                    System.out.print("Enter Advertiser ID: ");
                    int advertiserId = sc.nextInt();
                    sc.nextLine();

                    System.out.print("Enter Name: ");
                    String name = sc.nextLine();

                    System.out.print("Enter Email: ");
                    String email = sc.nextLine();

                    System.out.print("Enter Phone: ");
                    String phone = sc.nextLine();

                    system.addAdvertiser(
                        new Advertiser(advertiserId, name, email, phone)
                    );
                    break;

                case 2:
                    System.out.print("Enter Advertisement ID: ");
                    int adId = sc.nextInt();
                    sc.nextLine();

                    System.out.print("Enter Title: ");
                    String title = sc.nextLine();

                    System.out.print("Enter Description: ");
                    String description = sc.nextLine();

                    System.out.print("Enter Budget: ");
                    double budget = sc.nextDouble();
                    sc.nextLine();

                    System.out.print("Enter Platform: ");
                    String platform = sc.nextLine();

                    System.out.print("Enter Target Audience: ");
                    String audience = sc.nextLine();

                    System.out.print("Enter Advertiser ID: ");
                    int adAdvertiserId = sc.nextInt();
                    sc.nextLine();

                    DigitalAdvertisement ad = new DigitalAdvertisement(
                        adId, title, description, budget, platform, audience
                    );

                    system.addAdvertisement(ad, adAdvertiserId);
                    break;

                case 3:
                    System.out.print("Enter Campaign ID: ");
                    int campaignId = sc.nextInt();
                    sc.nextLine();

                    System.out.print("Enter Campaign Name: ");
                    String campaignName = sc.nextLine();

                    Campaign campaign = new Campaign(campaignId, campaignName);

                    System.out.print("Enter Advertisement ID: ");
                    int campaignAdId = sc.nextInt();

                    campaign.addAd(campaignAdId);
                    system.addCampaign(campaign);
                    break;

                case 4:
                    System.out.print("Enter Payment ID: ");
                    int paymentId = sc.nextInt();

                    System.out.print("Enter Advertisement ID: ");
                    int paymentAdId = sc.nextInt();

                    System.out.print("Enter Amount: ");
                    double amount = sc.nextDouble();
                    sc.nextLine();

                    System.out.print("Enter Payment Method: ");
                    String method = sc.nextLine();

                    system.addPayment(
                        new Payment(paymentId, paymentAdId, amount, method)
                    );
                    break;

                case 5:
                    sc.nextLine();
                    System.out.print("Enter Platform: ");
                    system.addPlatform(sc.nextLine());
                    break;

                case 6:
                    system.displayAds();
                    break;

                case 7:
                    system.displayAdvertisers();
                    break;

                case 8:
                    system.displayCampaigns();
                    break;

                case 9:
                    system.displayPayments();
                    break;

                case 10:
                    system.displayPlatforms();
                    break;

                case 11:
                    System.out.print("Enter Advertisement ID: ");
                    system.searchAd(sc.nextInt());
                    break;

                case 12:
                    System.out.print("Enter Advertiser ID: ");
                    system.searchAdvertiser(sc.nextInt());
                    break;

                case 13:
                    System.out.print("Enter Advertisement ID: ");
                    system.deleteAd(sc.nextInt());
                    break;

                case 14:
                    System.out.print("Enter Advertisement ID: ");
                    int statusId = sc.nextInt();
                    sc.nextLine();

                    System.out.print("Enter New Status: ");
                    String status = sc.nextLine();

                    system.updateStatus(statusId, status);
                    break;

                case 15:
                    system.showTotalBudget();
                    break;

                case 16:
                    system.showReport();
                    break;

                case 17:
                    system.showTotalAds();
                    break;

                case 18:
                    System.out.println("Thank you!");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }
        } while (choice != 18);

        sc.close();
    }
}
