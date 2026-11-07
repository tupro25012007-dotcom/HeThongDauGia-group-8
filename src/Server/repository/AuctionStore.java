package Server.repository;

import Shared.model.*;
import Shared.protocol.Wire;
import java.sql.*;
import java.util.*;

/** Each SQLite update commits the public history and private bidding rules together. */
public final class AuctionStore implements AutoCloseable {
    public record Account(String name, User.Role role, String salt, String hash) {}
    public record Rule(String bidder, long maximum, long increment, long priority) {}
    public record Lot(Auction auction, List<Rule> rules) {}
    private final Connection db;

    public AuctionStore(String url) throws SQLException {
        db = DriverManager.getConnection(url);
        try (var sql = db.createStatement()) {
            sql.execute("PRAGMA busy_timeout=5000");
            sql.execute("PRAGMA journal_mode=WAL");
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute("CREATE TABLE IF NOT EXISTS account(name TEXT PRIMARY KEY, role TEXT NOT NULL, salt TEXT NOT NULL, hash TEXT NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS lot(id TEXT PRIMARY KEY, body TEXT NOT NULL, next_change INTEGER)");
            sql.execute("CREATE INDEX IF NOT EXISTS due ON lot(next_change)");
            sql.execute("CREATE TABLE IF NOT EXISTS photo(lot TEXT PRIMARY KEY REFERENCES lot(id) ON DELETE CASCADE, image TEXT NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS watching(username TEXT NOT NULL, lot TEXT NOT NULL REFERENCES lot(id) ON DELETE CASCADE, PRIMARY KEY(username,lot))");
            sql.execute("CREATE TABLE IF NOT EXISTS profile(name TEXT PRIMARY KEY REFERENCES account(name),display TEXT NOT NULL DEFAULT '',locked INTEGER NOT NULL DEFAULT 0)");
        }
        initializeLedger();
    }
    private void initializeLedger() throws SQLException {
        try (var sql = db.createStatement();
             var row = sql.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='wallet_entry'")) {
            if (row.next()) return;
        }
        db.setAutoCommit(false);
        try (var sql = db.createStatement()) {
            sql.execute("CREATE TABLE wallet_entry(owner TEXT NOT NULL, ref TEXT NOT NULL, label TEXT NOT NULL, delta INTEGER NOT NULL, time INTEGER NOT NULL, PRIMARY KEY(owner,ref))");
            sql.execute("CREATE INDEX wallet_history ON wallet_entry(owner,time)");
            // Cover commitments made before wallets existed, once, in the schema transaction.
            for (var lot : list(null)) {
                var names = new HashSet<String>();
                if (lot.auction().leader() != null) names.add(lot.auction().leader());
                for (var rule : lot.rules()) names.add(rule.bidder());
                for (String name : names) {
                    long held = reservation(lot, name);
                    if (held > 0) record(name, "opening/" + lot.auction().id(), "Số dư chuyển đổi · " + lot.auction().name(), held);
                }
            }
            db.commit();
        } catch (SQLException | RuntimeException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
    }
    private static long reservation(Lot lot, String name) {
        var a = lot.auction();
        if (a.status() != Auction.Status.RUNNING && a.status() != Auction.Status.FINISHED) return 0;
        long leading = name.equals(a.leader()) ? a.price() : 0;
        if (a.status() == Auction.Status.FINISHED) return leading;
        long maximum = lot.rules().stream().filter(r -> r.bidder().equals(name) && r.maximum() > a.price())
                .mapToLong(Rule::maximum).max().orElse(0);
        return Math.max(leading, maximum);
    }
    public synchronized long held(String name, String exceptLot) throws SQLException {
        long total = 0;
        for (var lot : list(null)) if (!lot.auction().id().equals(exceptLot))
            total = Math.addExact(total, reservation(lot, name));
        return total;
    }
    public synchronized long balance(String owner) throws SQLException {
        try (var q = db.prepareStatement("SELECT COALESCE(SUM(delta),0) FROM wallet_entry WHERE owner=?")) {
            q.setString(1,owner);
            try (var row = q.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }
    public synchronized Long recordedAmount(String owner, String ref) throws SQLException {
        try (var q = db.prepareStatement("SELECT delta FROM wallet_entry WHERE owner=? AND ref=?")) {
            q.setString(1, owner); q.setString(2, ref);
            try (var row = q.executeQuery()) { return row.next() ? row.getLong(1) : null; }
        }
    }
    public synchronized void record(String owner, String ref, String label, long delta) throws SQLException {
        try (var q = db.prepareStatement("INSERT INTO wallet_entry VALUES(?,?,?,?,?)")) {
            q.setString(1, owner); q.setString(2, ref); q.setString(3, label);
            q.setLong(4, delta); q.setLong(5, System.currentTimeMillis()); q.executeUpdate();
        }
    }
    public synchronized Wallet wallet(String owner) throws SQLException {
        var entries = new ArrayList<Wallet.Entry>();
        try (var q = db.prepareStatement("SELECT ref,label,delta,time FROM wallet_entry WHERE owner=? ORDER BY rowid DESC LIMIT 100")) {
            q.setString(1,owner);
            try (var rows = q.executeQuery()) {
                while (rows.next()) entries.add(new Wallet.Entry(rows.getString(1),rows.getString(2),rows.getLong(3),rows.getLong(4)));
            }
        }
        return new Wallet(balance(owner), held(owner,null), entries);
    }
    public synchronized void pay(Auction paid) throws SQLException {
        String ref = "sale/" + paid.id();
        db.setAutoCommit(false);
        try {
            Math.addExact(balance(paid.seller()), paid.price());
            record(paid.leader(), ref, "Thanh toán · " + paid.name(), -paid.price());
            record(paid.seller(), ref, "Bán sản phẩm · " + paid.name(), paid.price());
            save(new Lot(paid,List.of()));
            db.commit();
        } catch (SQLException | RuntimeException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
    }
    public synchronized List<AccountProfile> profiles(String name) throws SQLException {
        var result = new ArrayList<AccountProfile>();
        String sql = "SELECT a.name,COALESCE(p.display,''),a.role,COALESCE(p.locked,0) FROM account a LEFT JOIN profile p ON p.name=a.name"
                + (name == null ? " ORDER BY a.name" : " WHERE a.name=?");
        try (var q = db.prepareStatement(sql)) {
            if (name != null) q.setString(1,name);
            try (var row = q.executeQuery()) {
                while (row.next()) result.add(new AccountProfile(row.getString(1),row.getString(2),User.Role.valueOf(row.getString(3)),row.getBoolean(4)));
            }
        }
        return result;
    }
    public synchronized void displayName(String name, String display) throws SQLException {
        try (var q = db.prepareStatement("INSERT INTO profile(name,display) VALUES(?,?) ON CONFLICT(name) DO UPDATE SET display=excluded.display")) {
            q.setString(1,name); q.setString(2,display); q.executeUpdate();
        }
    }
    public synchronized void lock(String name, boolean locked) throws SQLException {
        try (var q = db.prepareStatement("INSERT INTO profile(name,locked) VALUES(?,?) ON CONFLICT(name) DO UPDATE SET locked=excluded.locked")) {
            q.setString(1,name); q.setBoolean(2,locked); q.executeUpdate();
        }
    }
    public synchronized void password(Account a) throws SQLException {
        try (var q = db.prepareStatement("UPDATE account SET salt=?,hash=? WHERE name=?")) {
            q.setString(1,a.salt()); q.setString(2,a.hash()); q.setString(3,a.name()); q.executeUpdate();
        }
    }
    public synchronized void addAccount(Account account) throws SQLException {
        try (var q = db.prepareStatement("INSERT INTO account VALUES(?,?,?,?)")) {
            q.setString(1, account.name()); q.setString(2, account.role().name());
            q.setString(3, account.salt()); q.setString(4, account.hash());
            q.executeUpdate();
        }
    }
    public synchronized Account account(String name) throws SQLException {
        try (var q = db.prepareStatement("SELECT * FROM account WHERE name=?")) {
            q.setString(1, name);
            try (var row = q.executeQuery()) {
                return row.next() ? new Account(name, User.Role.valueOf(row.getString("role")), row.getString("salt"), row.getString("hash")) : null;
            }
        }
    }
    public synchronized Lot get(String id) throws SQLException {
        try (var q = db.prepareStatement("SELECT body FROM lot WHERE id=?")) {
            q.setString(1, id);
            try (var row = q.executeQuery()) {
                return row.next() ? Wire.JSON.fromJson(row.getString(1), Lot.class) : null;
            }
        }
    }
    public synchronized List<Lot> list(Long dueBy) throws SQLException {
        var lots = new ArrayList<Lot>();
        String query = "SELECT body FROM lot" + (dueBy == null ? " ORDER BY rowid" : " WHERE next_change<=?");
        try (var q = db.prepareStatement(query)) {
            if (dueBy != null) q.setLong(1, dueBy);
            try (var rows = q.executeQuery()) {
                while (rows.next()) lots.add(Wire.JSON.fromJson(rows.getString(1), Lot.class));
            }
        }
        return lots;
    }
    public synchronized void save(Lot lot) throws SQLException {
        var a = lot.auction();
        try (var q = db.prepareStatement("INSERT INTO lot VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,next_change=excluded.next_change")) {
            q.setString(1, a.id()); q.setString(2, Wire.JSON.toJson(lot));
            switch (a.status()) {
                case OPEN -> q.setLong(3, a.start());
                case RUNNING -> q.setLong(3, a.end());
                default -> q.setNull(3, Types.INTEGER);
            }
            q.executeUpdate();
        }
    }
    public synchronized void remove(String id) throws SQLException {
        try (var q = db.prepareStatement("DELETE FROM lot WHERE id=?")) {
            q.setString(1, id); q.executeUpdate();
        }
    }
    public synchronized String photo(String id) throws SQLException {
        try (var q = db.prepareStatement("SELECT image FROM photo WHERE lot=?")) {
            q.setString(1, id);
            try (var row = q.executeQuery()) { return row.next() ? row.getString(1) : null; }
        }
    }
    /** A missing change keeps the existing picture. An empty change removes it. */
    public synchronized void saveListing(Lot lot, String change) throws SQLException {
        if (change == null) { save(lot); return; }
        db.setAutoCommit(false);
        try {
            save(lot);
            String sql = change.isEmpty() ? "DELETE FROM photo WHERE lot=?" : "INSERT OR REPLACE INTO photo VALUES(?,?)";
            try (var q = db.prepareStatement(sql)) {
                q.setString(1, lot.auction().id());
                if (!change.isEmpty()) q.setString(2, change);
                q.executeUpdate();
            }
            db.commit();
        } catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    public synchronized Set<String> watched(String user) throws SQLException {
        var result = new HashSet<String>();
        try (var q = db.prepareStatement("SELECT lot FROM watching WHERE username=?")) {
            q.setString(1, user);
            try (var rows = q.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
        }
        return result;
    }
    public synchronized void watch(String user, String id, boolean enabled) throws SQLException {
        String sql = enabled ? "INSERT OR IGNORE INTO watching VALUES(?,?)" : "DELETE FROM watching WHERE username=? AND lot=?";
        try (var q = db.prepareStatement(sql)) {
            q.setString(1, user); q.setString(2, id); q.executeUpdate();
        }
    }
    @Override public synchronized void close() throws SQLException { db.close(); }
}
