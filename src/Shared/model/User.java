package Shared.model;

public abstract class User implements Entity {
    public enum Role { BIDDER, SELLER, ADMIN }
    private final String id;
    protected User(String id) { this.id = id; }
    public final String id() { return id; }
    public abstract Role role();
    public static User of(String id, Role role) {
        return switch (role) {
            case BIDDER -> new Bidder(id);
            case SELLER -> new Seller(id);
            case ADMIN -> new Admin(id);
        };
    }
}
