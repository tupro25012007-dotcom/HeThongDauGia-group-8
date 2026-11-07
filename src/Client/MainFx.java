package Client;

import Client.controller.*;
import Client.network.AuctionClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import java.io.*;

public final class MainFx extends Application {
    private Stage window;
    private AuctionClient session;
    private RoomController room;
    public static void main(String[] args) { launch(args); }
    @Override public void start(Stage stage) throws IOException {
        window = stage; window.setTitle("Phiên 08 · Sàn đấu giá");
        window.setMinWidth(940); window.setMinHeight(660);
        login(""); window.show();
    }
    private FXMLLoader load(String name) throws IOException {
        var loader = new FXMLLoader(getClass().getResource("/fxml/" + name + ".fxml"));
        Parent root = loader.load();
        if (window.getScene() == null) {
            var scene = new Scene(root, 1130, 750);
            scene.getStylesheets().add(getClass().getResource("/css/auction.css").toExternalForm());
            window.setScene(scene);
        } else window.getScene().setRoot(root);
        return loader;
    }
    public void enter(AuctionClient client, String name, String role) throws IOException {
        if (!client.connected()) { login("Mất kết nối. Vui lòng đăng nhập lại."); return; }
        session = client;
        room = load("Room").getController();
        room.start(this, client, name, role);
    }
    public void changed(AuctionClient client) { if (client == session && room != null) room.refresh(); }
    public void disconnected(AuctionClient client) {
        if (client != null && client == session) login("Mất kết nối. Đăng nhập lại để đồng bộ phiên.");
    }
    public void login(String message) {
        if (room != null) { room.stop(); room = null; }
        var old = session; session = null;
        if (old != null) old.close();
        try {
            LoginController login = load("Login").getController(); login.start(this, message);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    @Override public void stop() {
        if (room != null) room.stop();
        if (session != null) session.close();
    }
}
