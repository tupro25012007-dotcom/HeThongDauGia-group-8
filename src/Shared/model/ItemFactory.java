package Shared.model;

public final class ItemFactory {
    private ItemFactory() {}
    public static Item create(Item.Category category, String id, String name, String description) {
        return switch (category) {
            case ART -> new Art(id, name, description);
            case ELECTRONICS -> new Electronics(id, name, description);
            case VEHICLE -> new Vehicle(id, name, description);
        };
    }
}
