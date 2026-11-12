package Shared.model;
import Shared.model.UserRole;
import java.time.Instant;
public class Admin extends User {
    public Admin(String username) {
        super(username, UserRole.ADMIN);
    }
    public Admin(String id, Instant createdAt, String username) { super(id, createdAt, username, UserRole.ADMIN); }
    @Override
    public void printInfo() {
        System.out.println("=== ADMIN ===");
        super.printInfo();
    }
}
