package Shared.model;
import java.math.BigDecimal;
public class Vehicle extends Item {
    private final String manufacturer;
    private final String model;
    private final int year;
    public Vehicle(String name, String description, BigDecimal startingPrice, Seller seller, String manufacturer, String model, int year) {
        super(name, description, startingPrice, seller);
        if (manufacturer == null || manufacturer.isBlank()) {
            throw new IllegalArgumentException("Hãng xe không được để trống");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Dòng xe không được để trống");
        }
        if (year < 1886) {
            throw new IllegalArgumentException("Năm sản xuất không hợp lệ");
        }
        this.manufacturer = manufacturer.trim();
        this.model = model.trim();
        this.year = year;
    }
    public Vehicle(String id, String name, String description, BigDecimal price, Seller seller, String manufacturer, String model, int year) {
        super(id, name, description, price, seller); this.manufacturer = manufacturer; this.model = model; this.year = year;
    }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    @Override
    public void printInfo() {
        System.out.println("Phương tiện: " + getName());
        System.out.println("Hãng: " + manufacturer);
        System.out.println("Dòng xe: " + model);
        System.out.println("Năm sản xuất: " + year);
        System.out.println("Giá khởi điểm: " + getStartingPrice());
    }
}
