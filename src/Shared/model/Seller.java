package Shared.model;

public final class Seller extends User {
    public Seller(String id) { super(id); }
    @Override public Role role() { return Role.SELLER; }
}
