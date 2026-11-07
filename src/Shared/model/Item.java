package Shared.model;

public abstract class Item implements Entity {
    public enum Category {
        ELECTRONICS("Điện tử"), ART("Nghệ thuật"), VEHICLE("Phương tiện");
        private final String label;
        Category(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private final String id, name, description;
    protected Item(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
    public final String id() { return id; }
    public final String name() { return name; }
    public final String description() { return description; }
    public abstract Category category();
}
