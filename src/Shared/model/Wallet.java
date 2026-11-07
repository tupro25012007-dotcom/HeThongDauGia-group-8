package Shared.model;

import java.util.List;

public record Wallet(long balance, long held, List<Entry> recent) {
    public long available() { return balance - held; }
    public record Entry(String reference, String label, long delta, long time) {}
}
