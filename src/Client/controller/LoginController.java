package Client.controller;

import Client.MainFx;
import Client.network.AuctionClient;
import Shared.protocol.Wire;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class LoginController {
    @FXML private TextField name, host, port;
    @FXML private PasswordField password;
    @FXML private Label title, feedback;
    @FXML private Button submit, mode;
    @FXML private VBox roleBox;
    @FXML private ChoiceBox<String> role;
    private MainFx app;
    private boolean register;
    private final String defaultHost = setting("auction.host", "127.0.0.1");
    private final String defaultPort = setting("auction.port", "5050");

    private static String setting(String key, String fallback) {
        String value = System.getProperty(key, "").strip();
        return value.isEmpty() ? fallback : value;
    }
    public void start(MainFx app, String message) {
        this.app = app; feedback.setText(message);
        role.getItems().setAll("Người mua", "Người bán"); role.setValue("Người mua");
        host.setText(defaultHost);
        port.setText(defaultPort);
        roleBox.managedProperty().bind(roleBox.visibleProperty()); roleBox.setVisible(false);
    }
    @FXML private void guide() { GuideVideo.open(submit.getScene().getWindow()); }
    @FXML private void switchMode() {
        register = !register; roleBox.setVisible(register); feedback.setText("");
        title.setText(register ? "Tạo tài khoản" : "Đăng nhập");
        submit.setText(register ? "Tạo tài khoản" : "Vào phòng đấu");
        mode.setText(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký");
    }
    @FXML private void connect() {
        if (submit.isDisabled()) return;
        String user = name.getText(), pass = password.getText();
        if (user.isBlank() || pass.isEmpty()) { feedback.setText("Nhập tên tài khoản và mật khẩu."); return; }
        if (host.getText().isBlank()) host.setText(defaultHost);
        if (port.getText().isBlank()) port.setText(defaultPort);
        String address = host.getText().strip();
        int portNumber;
        try {
            portNumber = Integer.parseInt(port.getText().strip());
            if (portNumber < 1 || portNumber > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            feedback.setText("Cổng phải là số nguyên từ 1 đến 65535.");
            return;
        }
        String action = register ? "register" : "login";
        var data = Wire.fields("name", user, "password", pass, "role", role.getValue().equals("Người bán") ? "SELLER" : "BIDDER");
        submit.setDisable(true); mode.setDisable(true); feedback.setText("Đang kết nối…");
        Thread.startVirtualThread(() -> {
            var ref = new AtomicReference<AuctionClient>();
            try {
                var client = new AuctionClient(address, portNumber,
                        () -> Platform.runLater(() -> app.changed(ref.get())),
                        () -> Platform.runLater(() -> app.disconnected(ref.get())));
                ref.set(client);
                var info = client.call(action, data).getAsJsonObject();
                Platform.runLater(() -> {
                    try { app.enter(client, info.get("name").getAsString(), info.get("role").getAsString()); }
                    catch (Exception e) { client.close(); app.login("Không thể mở phòng đấu: " + e.getMessage()); }
                });
            } catch (Exception e) {
                if (ref.get() != null) ref.get().close();
                String reason = e instanceof IllegalArgumentException ? e.getMessage()
                        : "Không thể liên lạc với " + address + ":" + portNumber + ". Kiểm tra máy chủ đã chạy và thử lại.";
                Platform.runLater(() -> { submit.setDisable(false); mode.setDisable(false); feedback.setText(reason); });
            }
        });
    }
}
