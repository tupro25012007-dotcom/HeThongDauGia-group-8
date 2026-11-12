package Shared.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
public class BidTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final Bidder bidder;
    private final BigDecimal amount;
    private final LocalDateTime bidTime;
    public BidTransaction(Bidder bidder, BigDecimal amount) {
        this.id = UUID.randomUUID().toString();
        this.bidder = Objects.requireNonNull(bidder, "Người đấu giá không được để trống");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gía đấu phải lớn hơn 0");
        }
        this.amount = amount;
        this.bidTime = LocalDateTime.now();
    }
    public BidTransaction(String id, Bidder bidder, BigDecimal amount, LocalDateTime bidTime) {
        this.id = Objects.requireNonNull(id);
        this.bidder = Objects.requireNonNull(bidder);
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Giá bid không hợp lệ");
        this.amount = amount;
        this.bidTime = Objects.requireNonNull(bidTime);
    }
    public String getId() {
        return id;
    }
    public Bidder getBidder() {
        return bidder;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public LocalDateTime getBidTime() {
        return bidTime;
    }
}
