package Server.service;

import Server.repository.AuctionStore;
import Server.repository.AuctionStore.*;
import Shared.exception.AuctionException;
import Shared.model.*;
import Shared.model.Auction.Status;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;

public final class AuctionManager {
    public static final long MAX_PRICE = 999_999_999_999_999L;
    public record Listing(String name, String description, Item.Category category, long opening, long start, long end) {}
    private final AuctionStore store;
    private final Clock clock;
    private final Runnable onChange;

    public AuctionManager(AuctionStore store, Clock clock, Runnable onChange) {
        this.store = store; this.clock = clock; this.onChange = onChange;
    }
    public synchronized List<Auction> list() throws SQLException {
        tick();
        return store.list(null).stream().map(Lot::auction).toList();
    }
    public synchronized Wallet wallet(User user) throws SQLException {
        tick(); return store.wallet(user.id());
    }
    public synchronized Wallet moveMoney(User user, boolean deposit, long amount, String requestId) throws SQLException {
        require(amount > 0 && amount <= MAX_PRICE, "Số tiền phải là số nguyên dương trong giới hạn.");
        String ref = UUID.fromString(requestId).toString();
        long delta = deposit ? amount : -amount;
        Long previous = store.recordedAmount(user.id(), ref);
        if (previous != null) {
            require(previous == delta, "Mã giao dịch đã được dùng cho số tiền khác.");
            return wallet(user);
        }
        tick();
        long balance = store.balance(user.id());
        if (deposit) require(balance <= MAX_PRICE - amount, "Số dư sau khi nạp vượt giới hạn.");
        else require(amount <= balance - store.held(user.id(), null), "Số dư khả dụng không đủ để rút.");
        store.record(user.id(), ref, deposit ? "Nạp tiền mô phỏng" : "Rút tiền mô phỏng", delta);
        onChange.run(); return store.wallet(user.id());
    }
    private void funded(User user, String id, long amount) throws SQLException {
        long usable = store.balance(user.id()) - store.held(user.id(), id);
        require(amount <= usable, "Số dư khả dụng không đủ. Mở Tài khoản & ví để nạp tiền.");
    }
    public synchronized void tick() throws SQLException {
        long now = clock.millis();
        for (Lot lot : store.list(now)) {
            var a = lot.auction();
            var status = now >= a.end() ? Status.FINISHED : Status.RUNNING;
            save(a.withState(status, a.end(), a.bids()), lot.rules());
        }
    }
    public synchronized Auction publish(User user, String id, Listing form) throws SQLException {
        return publish(user, id, form, null);
    }
    public synchronized Auction publish(User user, String id, Listing form, String photo) throws SQLException {
        require(user.role() == User.Role.SELLER, "Chỉ người bán được đăng sản phẩm.");
        String revision = id == null ? null : editable(user, id).auction().photoVersion();
        if (photo != null) {
            if (!photo.isEmpty()) ProductPhoto.validate(photo);
            revision = photo.isEmpty() ? null : UUID.randomUUID().toString();
        }
        long now = clock.millis();
        require(form.name() != null && !form.name().isBlank() && form.name().length() <= 100, "Tên sản phẩm cần 1–100 ký tự.");
        require(form.description() != null && !form.description().isBlank() && form.description().length() <= 4000, "Mô tả cần 1–4000 ký tự.");
        require(form.category() != null, "Chọn loại sản phẩm.");
        require(form.opening() > 0 && form.opening() < MAX_PRICE, "Giá khởi điểm không hợp lệ.");
        require(form.start() > 0 && form.end() > Math.max(now, form.start()), "Giờ kết thúc phải sau giờ bắt đầu và sau hiện tại.");
        Item item = ItemFactory.create(form.category(), id == null ? UUID.randomUUID().toString() : id, form.name().strip(), form.description().strip());
        var a = new Auction(item.id(), user.id(), item.name(), item.description(), item.category(), form.opening(),
                form.start(), form.end(), form.start() > now ? Status.OPEN : Status.RUNNING, List.of(), revision);
        store.saveListing(new Lot(a, List.of()), photo);
        onChange.run();
        return a;
    }
    private Lot editable(User user, String id) throws SQLException {
        var lot = lookup(id); var a = lot.auction();
        require(a.seller().equals(user.id()), "Sản phẩm thuộc người bán khác.");
        require((a.status() == Status.OPEN || a.status() == Status.RUNNING) && a.bids().isEmpty(),
                "Chỉ sửa hoặc xóa phiên chưa có lượt đấu và chưa kết thúc.");
        return lot;
    }
    public synchronized void delete(User user, String id) throws SQLException {
        editable(user, id); store.remove(id); onChange.run();
    }
    public synchronized Auction bid(User user, String id, long amount) throws SQLException {
        var lot = bidding(user, id); var a = lot.auction();
        require(amount > a.price() && amount <= MAX_PRICE, "Giá đặt phải lớn hơn giá hiện tại và trong giới hạn.");
        require(!user.id().equals(a.leader()), "Bạn đang dẫn đầu phiên.");
        funded(user, id, amount);
        var history = new ArrayList<>(a.bids());
        history.add(new BidTransaction(user.id(), amount, clock.millis(), false));
        return resolve(lot, history, lot.rules());
    }
    public synchronized Auction autoBid(User user, String id, long maximum, long increment) throws SQLException {
        var lot = bidding(user, id); var a = lot.auction();
        require(maximum > a.price() && maximum <= MAX_PRICE && increment > 0 && increment <= MAX_PRICE, "Trần giá phải cao hơn giá hiện tại. Bước giá phải dương.");
        funded(user, id, maximum);
        var rules = new ArrayList<>(lot.rules());
        long order = rules.stream().mapToLong(Rule::priority).max().orElse(0) + 1;
        rules.removeIf(r -> r.bidder().equals(user.id()));
        rules.add(new Rule(user.id(), maximum, increment, order));
        return resolve(lot, new ArrayList<>(a.bids()), rules);
    }
    public synchronized Rule ownRule(User user, String id) throws SQLException {
        return lookup(id).rules().stream().filter(r -> r.bidder().equals(user.id())).findFirst().orElse(null);
    }
    public synchronized void disableAuto(User user, String id) throws SQLException {
        var lot = bidding(user, id);
        save(lot.auction(), lot.rules().stream().filter(r -> !r.bidder().equals(user.id())).toList());
    }
    private Lot bidding(User user, String id) throws SQLException {
        require(user.role() == User.Role.BIDDER, "Chỉ người mua được đặt giá.");
        var lot = lookup(id);
        require(lot.auction().status() == Status.RUNNING, "Phiên chưa mở hoặc đã kết thúc.");
        return lot;
    }
    /** Resolve proxy competition directly at the runner-up ceiling, without a loop per đồng. */
    private Auction resolve(Lot lot, List<BidTransaction> history, List<Rule> rules) throws SQLException {
        var a = lot.auction();
        long now = clock.millis();
        require(now < a.end(), "Phiên đã kết thúc.");
        var bids = a.withState(a.status(), a.end(), history);
        var queue = new PriorityQueue<>(Comparator.comparingLong(Rule::maximum).reversed().thenComparingLong(Rule::priority));
        queue.addAll(rules);
        Rule first = queue.poll();
        if (first != null && first.maximum() > bids.price()) {
            long rival = queue.isEmpty() ? 0 : queue.peek().maximum();
            long target = bids.price();
            if (!first.bidder().equals(bids.leader()) || rival >= target)
                target = Math.min(first.maximum(), Math.max(target, rival) + first.increment());
            if (target > bids.price())
                history.add(new BidTransaction(first.bidder(), target, now, true));
        }
        long end = a.end();
        if (history.size() > a.bids().size() && end - now <= 10_000) end += 60_000;
        return save(a.withState(Status.RUNNING, end, history), rules);
    }
    public synchronized Auction settle(User user, String id, boolean pay) throws SQLException {
        var a = lookup(id).auction();
        if (pay) {
            require(a.status() == Status.FINISHED && user.id().equals(a.leader()), "Chỉ người thắng được xác nhận thanh toán.");
            funded(user,id,a.price());
            var paid = a.withState(Status.PAID, a.end(), a.bids());
            store.pay(paid); onChange.run(); return paid;
        } else {
            require(user.role() == User.Role.ADMIN, "Cần quyền quản trị.");
            require(a.status() != Status.PAID && a.status() != Status.CANCELED, "Phiên đã được xử lý.");
        }
        return save(a.withState(Status.CANCELED, a.end(), a.bids()), List.of());
    }
    public synchronized Auction finish(User user, String id) throws SQLException {
        require(user.role() == User.Role.ADMIN, "Cần quyền quản trị.");
        var a = lookup(id).auction();
        require(a.status() == Status.RUNNING, "Chỉ đóng sớm phiên đang đấu.");
        return save(a.withState(Status.FINISHED, clock.millis(), a.bids()), List.of());
    }
    private Lot lookup(String id) throws SQLException {
        tick(); var lot = store.get(id);
        require(lot != null, "Không tìm thấy phiên đấu giá.");
        return lot;
    }
    private Auction save(Auction a, List<Rule> rules) throws SQLException {
        store.save(new Lot(a, List.copyOf(rules))); onChange.run(); return a;
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AuctionException(message);
    }
}
