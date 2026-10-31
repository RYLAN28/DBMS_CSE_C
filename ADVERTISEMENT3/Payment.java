public class Payment {
    private int paymentId;
    private int adId;
    private double amount;
    private String method;
    private String status;

    public Payment(int paymentId, int adId, double amount, String method) {
        this.paymentId = paymentId;
        this.adId = adId;
        this.amount = amount;
        this.method = method;
        this.status = "Paid";
    }

    public int getPaymentId() { return paymentId; }
    public int getAdId() { return adId; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }

    public void display() {
        System.out.println("Payment ID: " + paymentId);
        System.out.println("Advertisement ID: " + adId);
        System.out.println("Amount: Rs. " + amount);
        System.out.println("Method: " + method);
        System.out.println("Status: " + status);
    }
}
