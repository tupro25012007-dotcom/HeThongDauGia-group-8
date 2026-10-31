package Shared.model;
import Shared.model.UserRole;
public class Admin extends User {
    public Admin(String username) {
        super(username, UserRole.ADMIN);
    }
    @Override
    public void printInfo() {
        System.out.println("=== ADMIN ===");
        super.printInfo();
    }
}
