package Shared.model;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
public abstract class Item {
    private final String id;
    private String name;
    private String description;
    private BigDecimal startingPrice;
    private final Seller seller;
    protected Item(String name, String description, BigDecimal startingPrice, Seller seller) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mô tả không được để trống");
        }
        if (startingPrice == null || startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gía khởi điểm phải lớn hơn 0");
        }
        this.seller = Objects.requireNonNull(seller, "Seller không được để trống");
        this.id = UUID.randomUUID().toString();
        this.name = name.trim();
        this.description = description.trim();
        this.startingPrice = startingPrice;
    }
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        this.name = name.trim();
    }

    public void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mô tả không được để trống");
        }
        this.description = description.trim();
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        if (startingPrice == null || startingPrice.signum() <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0");
        }
        this.startingPrice = startingPrice;
    }
    public Seller getSeller() {
        return seller;
    }
    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public BigDecimal getStartingPrice() {
        return startingPrice;
    }
    public abstract void printInfo();
}