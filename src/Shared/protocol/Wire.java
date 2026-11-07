package Shared.protocol;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.Socket;

/** One UTF-8 JSON object per line. Request and reply share an id; push events have id zero. */
public final class Wire {
    public static final Gson JSON = new Gson();
    public record Request(long id, String action, JsonObject data) {}
    public record Reply(long id, String error, JsonElement data) {}
    public static JsonObject fields(Object... pairs) {
        var data = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2)
            data.add((String) pairs[i], JSON.toJsonTree(pairs[i+1]));
        return data;
    }
    public static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }
    public static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }
    public static void send(BufferedWriter writer, Object message) throws IOException {
        String line = JSON.toJson(message);
        synchronized (writer) { writer.write(line); writer.newLine(); writer.flush(); }
    }
}
