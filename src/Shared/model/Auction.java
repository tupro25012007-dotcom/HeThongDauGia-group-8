package Shared.model;

import java.util.List;

/** Public snapshot. Private auto-bid ceilings stay on the server. */
public record Auction(String id, String seller, String name, String description, Item.Category category,
                      long opening, long start, long end, Status status, List<BidTransaction> bids, String photoVersion) implements Entity {
    public enum Status {
        OPEN("Sắp mở"), RUNNING("Đang đấu"), FINISHED("Đã kết thúc"), PAID("Đã thanh toán"), CANCELED("Đã hủy");
        private final String label;
        Status(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public Auction { bids = List.copyOf(bids); }
    public long price() { return bids.isEmpty() ? opening : bids.getLast().amount(); }
    public String leader() { return bids.isEmpty() ? null : bids.getLast().bidder(); }
    public Auction withState(Status next, long deadline, List<BidTransaction> history) {
        return new Auction(id, seller, name, description, category, opening, start, deadline, next, history, photoVersion);
    }
}
