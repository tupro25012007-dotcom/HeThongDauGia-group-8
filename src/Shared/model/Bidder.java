package Shared.model;
import Shared.model.UserRole;
public class Bidder extends User {
    public Bidder(String username) {
        super(username, UserRole.BIDDER);
    }
    @Override
    public void printInfo() {
        System.out.println("=== BIDDER ===");
        super.printInfo();
    }
}