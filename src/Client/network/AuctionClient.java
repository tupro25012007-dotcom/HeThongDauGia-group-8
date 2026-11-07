package Client.network;

import Shared.protocol.Wire;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AuctionClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final BufferedWriter out;
    private final ConcurrentMap<Long, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
    private final AtomicLong next = new AtomicLong();

    public AuctionClient(String host, int port, Runnable onChange, Runnable onDisconnect) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, port), 4000);
            out = Wire.writer(socket);
        } catch (IOException | RuntimeException e) { socket.close(); throw e; }
        Thread.startVirtualThread(() -> {
            try (var in = Wire.reader(socket)) {
                String line;
                while ((line = in.readLine()) != null) {
                    var reply = Wire.JSON.fromJson(line, Wire.Reply.class);
                    if (reply.id() == 0) { onChange.run(); continue; }
                    var waiting = pending.remove(reply.id());
                    if (waiting == null) continue;
                    if (reply.error() == null) waiting.complete(reply.data());
                    else waiting.completeExceptionally(new IllegalArgumentException(reply.error()));
                }
            } catch (IOException | JsonParseException ignored) {}
            finally {
                close();
                pending.values().forEach(f -> f.completeExceptionally(new IOException("Mất kết nối máy chủ.")));
                onDisconnect.run();
            }
        });
    }
    public JsonElement call(String action, JsonObject data) throws Exception {
        long id = next.incrementAndGet();
        var future = new CompletableFuture<JsonElement>();
        pending.put(id, future);
        try {
            Wire.send(out, new Wire.Request(id, action, data));
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        } finally { pending.remove(id); }
    }
    public boolean connected() { return !socket.isClosed(); }
    @Override public void close() { try { socket.close(); } catch (IOException ignored) {} }
}
