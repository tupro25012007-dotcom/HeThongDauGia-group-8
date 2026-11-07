package Server.network;

import Server.service.*;
import Server.repository.AuctionStore;
import Shared.model.*;
import Shared.exception.AuctionException;
import Shared.protocol.Wire;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.*;

public final class AuctionServer implements AutoCloseable {
    private final ServerSocket listener;
    private final AuctionStore store;
    private final Set<Peer> peers = ConcurrentHashMap.newKeySet();
    // Revocation waits for in-flight commands, then invalidates matching sessions before new commands start.
    private final java.util.concurrent.locks.ReentrantReadWriteLock sessions = new java.util.concurrent.locks.ReentrantReadWriteLock(true);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    public final AuctionManager manager;
    public final UserService users;

    public AuctionServer(AuctionStore store, String host, int port) throws IOException {
        this.store = store;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(host, port));
        manager = new AuctionManager(store, Clock.systemUTC(), this::notifyPeers);
        users = new UserService(store);
    }
    public int port() { return listener.getLocalPort(); }
    public void serve() throws IOException {
        timer.scheduleWithFixedDelay(() -> {
            try { manager.tick(); } catch (SQLException e) { System.err.println(e.getMessage()); }
        }, 0, 250, TimeUnit.MILLISECONDS);
        while (!listener.isClosed()) {
            try {
                Socket client = listener.accept();
                Peer peer = new Peer(client);
                peers.add(peer);
                Thread.startVirtualThread(peer);
            } catch (SocketException e) { if (!listener.isClosed()) throw e; }
        }
    }
    private void notifyPeers() {
        for (Peer peer : peers) if (peer.user != null) peer.changes.offer(true);
    }
    @Override public void close() throws IOException {
        listener.close(); timer.shutdownNow();
        for (Peer peer : peers) peer.socket.close();
    }
    private final class Peer implements Runnable {
        final Socket socket;
        final BlockingQueue<Boolean> changes = new ArrayBlockingQueue<>(1);
        volatile User user;
        Peer(Socket socket) { this.socket = socket; }
        @Override public void run() {
            Thread push = null;
            try (socket; var input = Wire.reader(socket); var output = Wire.writer(socket)) {
                push = Thread.startVirtualThread(() -> {
                    try {
                        while (changes.take()) Wire.send(output, new Wire.Reply(0, null, new JsonPrimitive("changed")));
                    } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    catch (IOException e) { try { socket.close(); } catch (IOException ignored) {} }
                });
                String line;
                while ((line = input.readLine()) != null) {
                    long id = -1;
                    Wire.Reply reply;
                    try {
                        var req = Wire.JSON.fromJson(line, Wire.Request.class);
                        id = req.id();
                        var guard = ("password".equals(req.action()) || "lockAccount".equals(req.action()))
                                ? sessions.writeLock() : sessions.readLock();
                        guard.lock();
                        try {
                            reply = new Wire.Reply(id, null, Wire.JSON.toJsonTree(dispatch(req)));
                        } finally { guard.unlock(); }
                    } catch (AuctionException e) { reply = new Wire.Reply(id, e.getMessage(), null); }
                    catch (SQLException e) {
                        System.err.println(e.getMessage());
                        reply = new Wire.Reply(id, "Không thể truy cập cơ sở dữ liệu.", null);
                    } catch (RuntimeException e) { reply = new Wire.Reply(id, "Dữ liệu yêu cầu không hợp lệ.", null); }
                    Wire.send(output, reply);
                }
            } catch (IOException ignored) { /* disconnect ends the socket-bound session */ }
            finally { peers.remove(this); if (push != null) push.interrupt(); }
        }
        private Object dispatch(Wire.Request request) throws SQLException {
            var data = request.data();
            String action = request.action();
            if (action.equals("echo")) return data;
            if (action.equals("login") || action.equals("register")) {
                String name = data.get("name").getAsString(), pass = data.get("password").getAsString();
                user = action.equals("login") ? users.login(name, pass)
                        : users.register(name, pass, User.Role.valueOf(data.get("role").getAsString()));
                return Wire.fields("name", user.id(), "role", user.role());
            }
            if (user == null) throw new AuctionException("Vui lòng đăng nhập.");
            String id = data.has("id") ? data.get("id").getAsString() : null;
            return switch (action) {
                case "profile" -> store.profiles(user.id()).getFirst();
                case "rename" -> { users.rename(user, data.get("display").getAsString()); notifyPeers(); yield true; }
                case "password" -> {
                    users.changePassword(user, data.get("current").getAsString(), data.get("next").getAsString());
                    revokeOthers(user.id()); yield true;
                }
                case "accounts" -> { requireAdmin(); yield store.profiles(null); }
                case "lockAccount" -> {
                    requireAdmin();
                    String target = data.get("target").getAsString();
                    var account = store.account(target);
                    if (account == null || account.role() == User.Role.ADMIN) throw new AuctionException("Chọn tài khoản người mua hoặc người bán.");
                    boolean lock = data.get("locked").getAsBoolean();
                    manager.tick();
                    if (lock && store.held(target, null) > 0) throw new AuctionException("Tài khoản còn tiền giữ cho phiên đấu.");
                    store.lock(target, lock); if (lock) revokeOthers(target); notifyPeers(); yield true;
                }
                case "wallet" -> manager.wallet(user);
                case "deposit","withdraw" -> manager.moveMoney(user, action.equals("deposit"), number(data,"amount"),data.get("requestId").getAsString());
                case "list" -> manager.list();
                case "publish" -> manager.publish(user, id, Wire.JSON.fromJson(data, AuctionManager.Listing.class),
                        data.has("photo") ? data.get("photo").getAsString() : null);
                case "photo" -> store.photo(id);
                case "watched" -> store.watched(user.id());
                case "watch" -> {
                    store.watch(user.id(), id, data.get("enabled").getAsBoolean());
                    for (Peer peer : peers) {
                        User viewer = peer.user;
                        if (viewer != null && viewer.id().equals(user.id())) peer.changes.offer(true);
                    }
                    yield true;
                }
                case "delete" -> { manager.delete(user, id); yield true; }
                case "bid" -> manager.bid(user, id, number(data, "amount"));
                case "auto" -> manager.autoBid(user, id, number(data, "maximum"), number(data, "increment"));
                case "ownRule" -> manager.ownRule(user, id);
                case "disableAuto" -> { manager.disableAuto(user, id); yield true; }
                case "finish" -> manager.finish(user, id);
                case "cancel", "pay" -> manager.settle(user, id, action.equals("pay"));
                default -> throw new AuctionException("Lệnh không được hỗ trợ.");
            };
        }
        private long number(JsonObject data, String key) {
            return data.get(key).getAsBigDecimal().longValueExact();
        }
        private void requireAdmin() {
            if (user.role() != User.Role.ADMIN) throw new AuctionException("Cần quyền quản trị.");
        }
        private void revokeOthers(String name) {
            for (var peer : peers) {
                var account = peer.user;
                if (peer != this && account != null && account.id().equals(name)) {
                    peer.user = null;
                    try { peer.socket.close(); } catch (IOException ignored) {}
                }
            }
        }
    }
}
