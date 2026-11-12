package Shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
public class AuctionEvent {
    private final String auctionId;
    private final BigDecimal currentPrice;
    private final String leaderUsername;
    private final LocalDateTime bidTime;
    private final LocalDateTime endTime;

    public AuctionEvent(
            String auctionId,
            BigDecimal currentPrice,
            String leaderUsername,
            LocalDateTime bidTime,
            LocalDateTime endTime
    ) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.leaderUsername = leaderUsername;
        this.bidTime = bidTime;
        this.endTime = endTime;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public String getLeaderUsername() {
        return leaderUsername;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
