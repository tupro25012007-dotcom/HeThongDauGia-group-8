package Shared.model;

public interface BidObserver {
    void onBidUpdated(AuctionEvent event);
}
