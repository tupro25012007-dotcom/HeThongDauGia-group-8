package Shared.model;

public final class Admin extends User {
    public Admin(String id) { super(id); }
    @Override public Role role() { return Role.ADMIN; }
}
