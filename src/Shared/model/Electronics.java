package Shared.model;
import java.math.BigDecimal;
public class Electronics extends Item {
    private final String brand;
    private String model;
    private final int warrantyMonths;
    public Electronics(String name, String description, BigDecimal startingPrice, Seller seller, String brand, String model, int warrantyMonths) {
        super(name, description, startingPrice, seller);
        if (brand == null || brand.isBlank()) {
            throw new IllegalArgumentException("Hãng sản xuất không được để trống");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model không được để trống");
        }
        if (warrantyMonths < 0) {
            throw new IllegalArgumentException("Thời hạn bảo hành phải lớn hơn 0");
        }
        this.brand = brand.trim();
        this.model = model.trim();
        this.warrantyMonths = warrantyMonths;
    }
    public Electronics(String id, String name, String description, BigDecimal price, Seller seller, String brand, String model, int warrantyMonths) {
        super(id, name, description, price, seller);
        this.brand = brand; this.model = model; this.warrantyMonths = warrantyMonths;
    }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public int getWarrantyMonths() { return warrantyMonths; }
    @Override
    public void printInfo() {
        System.out.println("Đồ điện tử " + getName());
        System.out.println("Hãng: " + brand);
        System.out.println("Model: " + model);
        System.out.println("Bảo hành: " + warrantyMonths + " tháng");
        System.out.println("Gía khởi điểm " + getStartingPrice());
    }
}
