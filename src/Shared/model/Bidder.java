package Shared.model;

public final class Bidder extends User {
    public Bidder(String id) { super(id); }
    @Override public Role role() { return Role.BIDDER; }
}
