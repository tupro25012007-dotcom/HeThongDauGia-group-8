package Shared.model;
import java.time.Instant;
import java.util.UUID;
public abstract class Entity {
    private final String id;
    private final Instant createdAt;
    protected Entity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }
    public String getId() {
        return id;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public abstract void printInfo();
}