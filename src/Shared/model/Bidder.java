package Shared.model;
import Shared.model.UserRole;
import java.time.Instant;
public class Bidder extends User {
    public Bidder(String username) {
        super(username, UserRole.BIDDER);
    }
    public Bidder(String id, Instant createdAt, String username) { super(id, createdAt, username, UserRole.BIDDER); }
    @Override
    public void printInfo() {
        System.out.println("=== BIDDER ===");
        super.printInfo();
    }
}
