import java.util.ArrayList;

public class Campaign {
    private int campaignId;
    private String name;
    private ArrayList<Integer> adIds;

    public Campaign(int campaignId, String name) {
        this.campaignId = campaignId;
        this.name = name;
        adIds = new ArrayList<>();
    }

    public int getCampaignId() { return campaignId; }
    public String getName() { return name; }
    public ArrayList<Integer> getAdIds() { return adIds; }

    public void addAd(int adId) {
        adIds.add(adId);
    }

    public void display() {
        System.out.println("Campaign ID: " + campaignId);
        System.out.println("Campaign Name: " + name);
        System.out.println("Advertisement IDs:");

        for (int id : adIds) {
            System.out.println(id);
        }
    }
}
