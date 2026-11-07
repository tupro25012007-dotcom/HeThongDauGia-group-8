package Shared.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuctionItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String category;
    private double startPrice;
    private double currentPrice;
    private double stepPrice;
    private double buyNowPrice;
    private String highestBidder;
    private String seller;
    private long endTimeMillis;
    private boolean closed;
    private int bidCount;
    private List<String> bidHistory = new ArrayList<String>();

    // Hỗ trợ Đặt giá tự động (Auto-Bid / Proxy Bidding)
    private String autoBidUser = "";
    private double autoBidMaxAmount = 0;

    public AuctionItem(String id, String name, double currentPrice, double stepPrice) {
        this(id, name, "Công nghệ", currentPrice, stepPrice, currentPrice * 1.5, "Hệ thống", 300);
    }

    public AuctionItem(String id, String name, double currentPrice, double stepPrice, String seller) {
        this(id, name, "Tài sản đấu giá", currentPrice, stepPrice, currentPrice * 1.5, seller, 300);
    }

    public AuctionItem(String id, String name, String category, double startPrice, double stepPrice,
                       double buyNowPrice, String seller, int durationSeconds) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.stepPrice = stepPrice;
        this.buyNowPrice = buyNowPrice;
        this.highestBidder = "Chưa có";
        this.seller = seller;
        this.endTimeMillis = System.currentTimeMillis() + (durationSeconds * 1000L);
        this.closed = false;
        this.bidCount = 0;
        addHistoryEntry("Khởi tạo phiên bởi " + seller + " (Giá KĐ: " + String.format("%,.0f", startPrice) + " ₫)");
    }

    private void addHistoryEntry(String text) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        bidHistory.add(0, "[" + time + "] " + text);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getStartPrice() { return startPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public double getStepPrice() { return stepPrice; }
    public double getBuyNowPrice() { return buyNowPrice; }
    public String getHighestBidder() { return highestBidder; }
    public String getSeller() { return seller; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public int getBidCount() { return bidCount; }
    public List<String> getBidHistory() { return bidHistory; }
    public String getAutoBidUser() { return autoBidUser; }
    public double getAutoBidMaxAmount() { return autoBidMaxAmount; }

    public void setAutoBid(String user, double maxAmount) {
        this.autoBidUser = user;
        this.autoBidMaxAmount = maxAmount;
        addHistoryEntry("🤖 " + user + " đã bật Auto-Bid (Trần tối đa: " + String.format("%,.0f", maxAmount) + " ₫)");
    }

    public long getRemainingSeconds() {
        if (closed) return 0;
        long diff = (endTimeMillis - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, diff);
    }

    public boolean isEnded() {
        if (closed) return true;
        if (System.currentTimeMillis() >= endTimeMillis) {
            closed = true;
            return true;
        }
        return false;
    }

    public void updateBid(double newPrice, String bidder) {
        updateBid(newPrice, bidder, false);
    }

    public void updateBid(double newPrice, String bidder, boolean isAuto) {
        this.currentPrice = newPrice;
        this.highestBidder = bidder;
        this.bidCount++;
        String prefix = isAuto ? "🤖 [AUTO-BID] " : "🔨 ";
        addHistoryEntry(prefix + bidder + " trả giá " + String.format("%,.0f", newPrice) + " ₫");

        long remaining = getRemainingSeconds();
        if (remaining > 0 && remaining < 30) {
            this.endTimeMillis += 30000L;
            addHistoryEntry("⏱ Anti-Sniping: Tự động cộng thêm +30 giây!");
        }
        if (buyNowPrice > 0 && newPrice >= buyNowPrice) {
            this.closed = true;
            this.endTimeMillis = System.currentTimeMillis();
            addHistoryEntry("🏆 Đạt ngưỡng Mua Ngay! Phiên đã chốt cho " + bidder);
        }
    }

    public void buyNow(String bidder) {
        this.currentPrice = this.buyNowPrice;
        this.highestBidder = bidder;
        this.bidCount++;
        this.closed = true;
        this.endTimeMillis = System.currentTimeMillis();
        addHistoryEntry("⚡ [MUA NGAY] " + bidder + " chốt đứt giá " + String.format("%,.0f", buyNowPrice) + " ₫");
    }

    public void extendTime(int seconds) {
        if (!isEnded()) {
            this.endTimeMillis += seconds * 1000L;
            addHistoryEntry("⏳ Người bán gia hạn thêm +" + seconds + " giây");
        }
    }

    public void closeEarly() {
        this.closed = true;
        this.endTimeMillis = System.currentTimeMillis();
        addHistoryEntry("🔒 Người bán đã chốt phiên đấu giá sớm! Người thắng: " + highestBidder);
    }
}
