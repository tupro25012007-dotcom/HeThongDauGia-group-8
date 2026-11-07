package Shared.model;

public final class Art extends Item {
    public Art(String id, String name, String description) { super(id, name, description); }
    @Override public Category category() { return Category.ART; }
}
