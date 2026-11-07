package Shared.model;

public final class Electronics extends Item {
    public Electronics(String id, String name, String description) { super(id, name, description); }
    @Override public Category category() { return Category.ELECTRONICS; }
}
