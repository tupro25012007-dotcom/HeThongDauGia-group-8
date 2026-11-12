package Shared.model;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public class Auction implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final Item item;
    private final Seller seller;
    private final LocalDateTime startTime;
    private volatile LocalDateTime endTime;
    private BigDecimal currentPrice;
    private AuctionStatus status;
    private String imagePath;
    public Auction(Item item, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = UUID.randomUUID().toString();
        this.item = Objects.requireNonNull(item, "Sản phẩm không được để trống");
        this.seller = item.getSeller();
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Thời gian bắt đầu và kết thúc không được");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentPrice = item.getStartingPrice();
        this.status = AuctionStatus.OPEN;
    }
    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal currentPrice, AuctionStatus status, Bidder winner) {
        this(id, item, startTime, endTime, currentPrice, status, winner, null);
    }

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime,
                   BigDecimal currentPrice, AuctionStatus status, Bidder winner,
                   String imagePath) {
        this.id = Objects.requireNonNull(id);
        this.item = Objects.requireNonNull(item);
        this.seller = item.getSeller();
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = Objects.requireNonNull(endTime);
        this.currentPrice = Objects.requireNonNull(currentPrice);
        this.status = Objects.requireNonNull(status);
        this.winner = winner;
        this.imagePath = imagePath;
    }
    public String getId() {
        return id;
    }
    public Item getItem() {
        return item;
    }
    public Seller getSeller() {
        return seller;
    }
    public LocalDateTime getStartTime() {
        return startTime;
    }
    public LocalDateTime getEndTime() {
        return endTime;
    }
    public void extendEndTime(java.time.Duration extension) {
        if (extension == null || extension.isNegative() || extension.isZero()) {
            throw new IllegalArgumentException("Thời gian gia hạn phải lớn hơn 0");
        }
        endTime = endTime.plus(extension);
    }
    public void restoreEndTime(LocalDateTime endTime) {
        this.endTime = Objects.requireNonNull(endTime);
    }
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
    public AuctionStatus getStatus() {
        return status;
    }
    public String getImagePath() {
        return imagePath;
    }
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    public void updateCurrentPrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(currentPrice) <= 0) {
            throw new IllegalArgumentException("Gía mới phải cao hơn giá hiện tại");
        }
        this.currentPrice = newPrice;
    }
    public void updateStatus(LocalDateTime now) {
        if (status == AuctionStatus.PAID || status == AuctionStatus.CANCELED) {
            return;
        }
        if (now.isBefore(startTime)) {
            status = AuctionStatus.OPEN;
        } else if (now.isBefore(endTime)) {
            status = AuctionStatus.RUNNING;
        } else {
            status = AuctionStatus.FINISHED;
        }
    }
    private final List<BidTransaction> bidHistory = new ArrayList<>();
    public void addBidTransaction(BidTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Lượt đấu giá không được để trống");
        }

        bidHistory.add(transaction);
    }
    public void rollbackBid(BidTransaction transaction, BigDecimal previousPrice) {
        if (!bidHistory.isEmpty() && bidHistory.get(bidHistory.size() - 1) == transaction) {
            bidHistory.remove(bidHistory.size() - 1);
            currentPrice = previousPrice;
        }
    }
    public List<BidTransaction> getBidHistory() {
        return List.copyOf(bidHistory);
    }
    private Bidder winner;
    public Bidder getWinner() {
        return winner;
    }
    public void setWinner(Bidder winner) {
        this.winner = winner;
    }
    public void setStatus(AuctionStatus status) {
        this.status = status;
    }
    private transient List<BidObserver> observers = new CopyOnWriteArrayList<>();
    public void addObserver(BidObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer không được để trống");
        }
        observers.add(observer);
    }
    public void removeObserver(BidObserver observer) {
        observers.remove(observer);
    }
    public void notifyBidUpdated(AuctionEvent event) {
        for (BidObserver observer: observers) {
            observer.onBidUpdated(event);
        }
    }
}
