package Shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Wallet snapshot: total balance, amount reserved by active auctions, and recent ledger entries. */
public record Wallet(BigDecimal balance, BigDecimal held, List<Entry> recent) {
    public Wallet {
        if (balance == null || held == null || balance.signum() < 0 || held.signum() < 0
                || held.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Số dư ví không hợp lệ");
        }
        recent = recent == null ? List.of() : List.copyOf(recent);
    }

    public BigDecimal available() {
        return balance.subtract(held);
    }

    public record Entry(String ownerId, String reference, String label, BigDecimal delta, LocalDateTime time) {}
}
