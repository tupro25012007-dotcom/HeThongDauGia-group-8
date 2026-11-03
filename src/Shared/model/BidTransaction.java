package Shared.model;

public record BidTransaction(String bidder, long amount, long time, boolean automatic) {}
