package Server.repository;

import Shared.model.AuctionItem;
import Shared.model.WalletInfo;

import java.text.SimpleDateFormat;
import java.util.*;

public class AuctionRepository {
    private Map<String, AuctionItem> items = new LinkedHashMap<String, AuctionItem>();
    private Map<String, Double> userWallets = new LinkedHashMap<String, Double>();
    private Map<String, List<String>> walletHistory = new LinkedHashMap<String, List<String>>();
    private int sequence = 5;

    public AuctionRepository() {
        initUser("Nguyễn Văn A", 150000000.0);
        initUser("Trần Thị B", 250000000.0);
        initUser("Lê Hoàng C", 95000000.0);
        initUser("Phạm Minh D", 500000000.0);

        items.put("SP01", new AuctionItem("SP01", "MacBook Pro 16 M3 Max 64GB", "Công nghệ",
                45000000, 1000000, 68000000, "Admin", 300));
        items.put("SP02", new AuctionItem("SP02", "iPhone 16 Pro Max 1TB Desert Titanium", "Di động",
                28000000, 500000, 42000000, "Admin", 240));
        items.put("SP03", new AuctionItem("SP03", "Đồng hồ Omega Speedmaster Moonwatch", "Đồng hồ",
                85000000, 2000000, 135000000, "Trần Thị B", 420));
        items.put("SP04", new AuctionItem("SP04", "Tranh Sơn Dầu Phố Cổ Hà Nội (Bản Gốc)", "Nghệ thuật",
                15000000, 500000, 28000000, "Lê Hoàng C", 210));
    }

    private void initUser(String username, double initialBalance) {
        userWallets.put(username, initialBalance);
        List<String> logs = new ArrayList<String>();
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        logs.add("[" + time + "] Khởi tạo ví tài khoản: +" + String.format("%,.0f", initialBalance) + " ₫");
        walletHistory.put(username, logs);
    }

    private void ensureUserExists(String username) {
        if (!userWallets.containsKey(username)) {
            initUser(username, 100000000.0);
        }
    }

    public synchronized void addWalletLog(String username, String entry) {
        ensureUserExists(username);
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        walletHistory.get(username).add(0, "[" + time + "] " + entry);
    }

    public synchronized double deposit(String username, double amount, String method) {
        ensureUserExists(username);
        double newTotal = userWallets.get(username) + amount;
        userWallets.put(username, newTotal);
        addWalletLog(username, "💳 Nạp tiền qua " + method + ": +" + String.format("%,.0f", amount) + " ₫");
        return newTotal;
    }

    public synchronized AuctionItem getItem(String id) {
        return items.get(id);
    }

    public synchronized Map<String, AuctionItem> getAllItems() {
        return items;
    }

    public synchronized List<AuctionItem> getItemList() {
        return new ArrayList<AuctionItem>(items.values());
    }

    public synchronized AuctionItem addNewItem(String name, String category, double startPrice,
                                               double stepPrice, double buyNowPrice, String seller, int durationSec) {
        String id = String.format("SP%02d", sequence++);
        AuctionItem newItem = new AuctionItem(id, name, category, startPrice, stepPrice, buyNowPrice, seller, durationSec);
        items.put(id, newItem);
        return newItem;
    }

    public synchronized double getWalletBalance(String username) {
        ensureUserExists(username);
        return userWallets.get(username);
    }

    public synchronized double getLockedEscrow(String username) {
        ensureUserExists(username);
        double locked = 0;
        for (AuctionItem item : items.values()) {
            if (!item.isEnded() && username.equalsIgnoreCase(item.getHighestBidder())) {
                locked += item.getCurrentPrice();
            }
        }
        return locked;
    }

    public synchronized double getSpentOnClosed(String username) {
        ensureUserExists(username);
        double spent = 0;
        for (AuctionItem item : items.values()) {
            if (item.isEnded() && username.equalsIgnoreCase(item.getHighestBidder())) {
                spent += item.getCurrentPrice();
            }
        }
        return spent;
    }

    public synchronized double getAvailableBalance(String username) {
        return Math.max(0, getWalletBalance(username) - getSpentOnClosed(username) - getLockedEscrow(username));
    }

    public synchronized WalletInfo getWalletInfo(String username) {
        ensureUserExists(username);
        return new WalletInfo(
                username,
                getWalletBalance(username),
                getLockedEscrow(username),
                getSpentOnClosed(username),
                walletHistory.get(username)
        );
    }
}
