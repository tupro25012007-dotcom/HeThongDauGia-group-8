package Shared.model;
import Shared.model.UserRole;
import java.time.Instant;
public class Seller extends User {
    public Seller(String username) {
        super(username, UserRole.SELLER);
    }
    public Seller(String id, Instant createdAt, String username) { super(id, createdAt, username, UserRole.SELLER); }
    @Override
    public void printInfo() {
        System.out.println("=== SELLER ===");
        super.printInfo();
    }
}
