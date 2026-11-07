package Shared.model;

public final class Vehicle extends Item {
    public Vehicle(String id, String name, String description) { super(id, name, description); }
    @Override public Category category() { return Category.VEHICLE; }
}
