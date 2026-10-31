import java.sql.*;

// our own exception for the advertising system
class InvalidBudgetException extends Exception {
    public InvalidBudgetException(String message) {
        super(message);
    }
}

public class ErrorHandlingDemo {

    // "throws" means: this method may throw this exception
    static void checkBudget(double budget) throws InvalidBudgetException {
        if (budget <= 0) {
            throw new InvalidBudgetException("Budget must be more than 0, but got " + budget);
        }
        System.out.println("Budget " + budget + " is valid.");
    }

    public static void main(String[] args) {

        // 1. try - catch : wrong number typed by the user
        System.out.println("--- 1. NumberFormatException ---");
        try {
            double budget = Double.parseDouble("abc");   // not a number
            System.out.println(budget);
        } catch (NumberFormatException e) {
            System.out.println("Please enter the budget as a number.");
        }

        // 2. our own exception
        System.out.println("\n--- 2. Custom exception ---");
        try {
            checkBudget(25000);     // fine
            checkBudget(-500);      // throws our exception
        } catch (InvalidBudgetException e) {
            System.out.println("Error: " + e.getMessage());
        }

        // 3. multiple catch blocks
        System.out.println("\n--- 3. Multiple catch ---");
        try {
            int clicks = 0;
            int costPerClick = 1000 / clicks;               // divide by zero
            System.out.println(costPerClick);
        } catch (ArithmeticException e) {
            System.out.println("Clicks are 0, cannot calculate cost per click.");
        } catch (Exception e) {
            System.out.println("Something else went wrong.");
        }

        // 4. array / null problems
        System.out.println("\n--- 4. Array and null errors ---");
        try {
            String[] platforms = {"Instagram", "YouTube", "Google"};
            System.out.println(platforms[5]);               // there is no index 5
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("That platform number does not exist.");
        }

        try {
            String title = null;
            System.out.println(title.length());             // null has no length
        } catch (NullPointerException e) {
            System.out.println("Title is empty (null).");
        }

        // 5. finally : always runs, even if there was an error
        System.out.println("\n--- 5. finally ---");
        try {
            System.out.println("Saving advertisement...");
            int x = 10 / 0;
        } catch (ArithmeticException e) {
            System.out.println("Save failed.");
        } finally {
            System.out.println("Finally block runs every time (close files / connections here).");
        }

        // 6. database error : wrong password
        System.out.println("\n--- 6. SQLException ---");
        try {
            Connection con = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/advertising_db", "postgres", "wrong_password");
            con.close();
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }

        System.out.println("\nProgram finished normally, it did not crash.");
    }
}
