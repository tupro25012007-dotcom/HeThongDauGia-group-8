package Server.service;

import Server.repository.ItemRepository;
import Shared.model.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class ProductService {
    private final Map<String, Item> itemsById = new LinkedHashMap<>(); // giữ thứ tự các sản phẩm được thêm vào
    private final ItemRepository itemRepository = new ItemRepository(); // cái này làm việc với bảng items trogn sqlite

    public synchronized void loadFromDatabase(UserService userService) {
        try {
            for (ItemRepository.ItemRow row : itemRepository.findAll()) {
                Seller seller = userService.findById(row.sellerId())
                        .filter(Seller.class::isInstance).map(Seller.class::cast)
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy Seller của sản phẩm " + row.id()));
                Item item = fromRow(row, seller);
                itemsById.put(item.getId(), item);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Không nạp được sản phẩm từ database", ex);
        }
    }

    public synchronized void addItem(Item item) {
        if (item == null) throw new IllegalArgumentException("Sản phẩm không được để trống");
        if (itemsById.containsKey(item.getId())) throw new IllegalArgumentException("Sản phẩm đã tồn tại");
        persist(item);
        itemsById.put(item.getId(), item);
    }

    public synchronized List<Item> getAllItems() { return new ArrayList<>(itemsById.values()); }

    public synchronized Optional<Item> findById(String id) {
        return id == null || id.isBlank() ? Optional.empty() : Optional.ofNullable(itemsById.get(id));
    }

    public synchronized List<Item> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return itemsById.values().stream().filter(i -> i.getName().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    public synchronized void updateItem(String itemId, Seller seller, String newName,
                                        String newDescription, BigDecimal newStartingPrice) {
        Item item = getItemOrThrow(itemId);
        checkOwner(item, seller);
        item.setName(newName);
        item.setDescription(newDescription);
        item.setStartingPrice(newStartingPrice);
        persist(item);
    }

    public synchronized void deleteItem(String itemId, Seller seller) {
        Item item = getItemOrThrow(itemId);
        checkOwner(item, seller);
        try {
            if (!itemRepository.deleteById(itemId)) throw new NoSuchElementException("Không tìm thấy sản phẩm trong database");
            itemsById.remove(itemId);
        } catch (SQLException ex) {
            throw new RuntimeException("Không xóa được sản phẩm khỏi database", ex);
        }
    }

    private void persist(Item item) {
        String type;
        if (item instanceof Electronics) type = "ELECTRONICS";
        else if (item instanceof Art) type = "ART";
        else if (item instanceof Vehicle) type = "VEHICLE";
        else throw new IllegalArgumentException("Loại sản phẩm không được hỗ trợ");
        try {
            itemRepository.save(new ItemRepository.ItemRow(item.getId(), item.getName(), item.getDescription(),
                    item.getStartingPrice(), item.getSeller().getId(), type, encodeDetails(item)));
        } catch (SQLException ex) {
            throw new RuntimeException("Không lưu được sản phẩm vào database", ex);
        }
    }

    private Item fromRow(ItemRepository.ItemRow row, Seller seller) {
        String[] d = decodeDetails(row.details());
        return switch (row.type()) {
            case "ELECTRONICS" -> new Electronics(row.id(), row.name(), row.description(), row.startingPrice(), seller,
                    d[0], d[1], Integer.parseInt(d[2]));
            case "ART" -> new Art(row.id(), row.name(), row.description(), row.startingPrice(), seller, d[0], d[1]);
            case "VEHICLE" -> new Vehicle(row.id(), row.name(), row.description(), row.startingPrice(), seller,
                    d[0], d[1], Integer.parseInt(d[2]));
            default -> throw new IllegalArgumentException("Loại sản phẩm không hợp lệ trong database: " + row.type());
        };
    }

    private String encodeDetails(Item item) {
        if (item instanceof Electronics e) return enc(e.getBrand()) + "|" + enc(e.getModel()) + "|" + e.getWarrantyMonths();
        if (item instanceof Art a) return enc(a.getArtist()) + "|" + enc(a.getMaterial());
        if (item instanceof Vehicle v) return enc(v.getManufacturer()) + "|" + enc(v.getModel()) + "|" + v.getYear();
        return "";
    }

    private String[] decodeDetails(String details) {
        if (details == null || details.isBlank()) throw new IllegalStateException("Thiếu thông tin riêng của sản phẩm trong database");
        String[] parts = details.split("\\|", -1);
        for (int i = 0; i < parts.length - 1; i++) parts[i] = new String(Base64.getUrlDecoder().decode(parts[i]), StandardCharsets.UTF_8);
        return parts;
    }
    private String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private Item getItemOrThrow(String id) { Item item = itemsById.get(id); if (item == null) throw new NoSuchElementException("Không tìm thấy sản phẩm"); return item; }
    private void checkOwner(Item item, Seller seller) {
        if (seller == null) throw new SecurityException("Bạn cần đăng nhập bằng tài khoản Seller");
        if (!item.getSeller().getId().equals(seller.getId())) throw new SecurityException("Bạn chỉ được sửa hoặc xóa sản phẩm của mình");
    }
}
