package Shared.model;
import java.math.BigDecimal;
import java.util.Locale;
public final class ItemFactory {
    private ItemFactory() {}
    public static Item createItem(String type, String name, String description, BigDecimal startingPrice, Seller seller, String... details) { // String... nhận số lượng chuỗi thay đổi nhưng bên trong xem như mảng String[]
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Loại sản phẩm không được để trống");
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        return switch(normalizedType) {
            case "ELECTRONICS" -> createElectronics(name, description, startingPrice, seller, details);
            case "ART" -> createArt(name, description, startingPrice, seller, details);
            case "VEHICLE" -> createVehicle(name, description, startingPrice, seller, details);
            default -> throw new IllegalArgumentException("Loại sản phẩm không được hỗ trợ: " + type);
        };
    }
    private static Electronics createElectronics(String name, String description, BigDecimal price, Seller seller, String[] details) {
        requireDetailCount("Electronics", details, 3);
        int warrantyMonths;
        warrantyMonths = Integer.parseInt(details[2]);
        return new Electronics(name, description, price, seller, details[0], details[1], warrantyMonths);
    }
    private static Art createArt(String name, String description, BigDecimal price, Seller seller, String[] details) {
        requireDetailCount("Art", details, 2);
        return new Art(name, description, price, seller, details[0], details[1]);
    }
    private static Vehicle createVehicle(String name, String description, BigDecimal price, Seller seller, String[] details) {
        requireDetailCount("Vehicle", details, 3);
        int year;
        year = Integer.parseInt(details[2]);
        return new Vehicle(name, description, price, seller, details[0], details[1], year);
    }
    private static void requireDetailCount(String type, String[] details, int expected) {
        if (details == null || details.length != expected) {
            throw new IllegalArgumentException(type + " cần " + expected + " thông tin riêng");
        }
    }
}
