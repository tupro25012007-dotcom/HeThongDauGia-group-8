package Shared.model;
import Shared.model.UserRole;
public class Seller extends User {
    public Seller(String username) {
        super(username, UserRole.SELLER);
    }
    @Override
    public void printInfo() {
        System.out.println("=== SELLER ===");
        super.printInfo();
    }
}