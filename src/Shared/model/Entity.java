package Shared.model;
import java.time.Instant;  //sử dụng đ biểu diễn mốc thời gian
import java.util.UUID;  // dùng để sinh mã định danh ngẫu nhiên
import java.io.Serializable;
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;  // mã phiên bản khi dùng serialize
    private final String id;
    private final Instant createdAt;
    protected Entity() {
        this.id = UUID.randomUUID().toString();  // biểu thị id của đối tượng (bất biến)
        this.createdAt = Instant.now();  // biểu thị thời gian tạo đối tượng
    }
    protected Entity(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }
    public String getId() {
        return id;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public abstract void printInfo();
}
