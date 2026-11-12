package Shared.model;
import java.math.BigDecimal;
public class Art extends Item {
    private final String artist;
    private final String material;
    public Art(String name, String description, BigDecimal startingPrice, Seller seller, String artist, String material) {
        super(name, description, startingPrice, seller);
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("Tên nghệ sĩ không được để trống");
        }
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("Chất liệu không được để trống");
        }
        this.artist = artist.trim();
        this.material = material.trim();
    }
    public Art(String id, String name, String description, BigDecimal price, Seller seller, String artist, String material) {
        super(id, name, description, price, seller); this.artist = artist; this.material = material;
    }
    public String getArtist() { return artist; }
    public String getMaterial() { return material; }
    @Override
    public void printInfo() {
        System.out.println("Tác phẩm nghệ thuật: " + getName());
        System.out.println("Nghệ sĩ: " + artist);
        System.out.println("Chất liệu: " + material);
        System.out.println("Gía khởi điểm: " + getStartingPrice());
    }
}
