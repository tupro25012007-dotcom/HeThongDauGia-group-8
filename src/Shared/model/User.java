package Shared.model;
import java.time.Instant;
import java.math.BigDecimal;

public abstract class User extends Entity {
    private BigDecimal balance = BigDecimal.ZERO;
    private String username;
    private final UserRole role;
    protected User(String username, UserRole role) {
        super();
        if (username == null) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role không được để trống");
        }
        this.username = username.trim();
        this.role = role;
    }
    protected User(String id, Instant createdAt, String username, UserRole role) {
        super(id, createdAt);
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username không được để trống");
        if (role == null) throw new IllegalArgumentException("Role không được để trống");
        this.username = username.trim();
        this.role = role;
    }
    public UserRole getRole() {
        return role;
    }
    public BigDecimal getBalance() {
        return balance;
    }
    public void setBalance(BigDecimal balance) {
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("Số dư không được âm");
        }
        this.balance = balance;
    }
    public String getUsername() {
        return username;
    }
    @Override
    public void printInfo() {
        System.out.println("ID: " + getId());
        System.out.println("Username: " + username);
        System.out.println("Role: " + role);
        System.out.println("Created at: " + getCreatedAt());
    }
}
