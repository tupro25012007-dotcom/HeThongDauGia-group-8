package Client.controller;

import Client.network.AuctionClient;
import Client.state.Display;
import Shared.model.*;
import Shared.protocol.Wire;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;
import java.util.concurrent.TimeoutException;
import static Shared.protocol.Wire.fields;

final class AccountDialog {
    private final AuctionClient client;
    private final Dialog<Void> dialog = new Dialog<>();
    private final TabPane tabs = new TabPane();
    private final Label status = new Label();
    private final Label total = new Label(), held = new Label(), available = new Label();
    private final TextField amount = new TextField(), display = new TextField();
    private final PasswordField current = new PasswordField(), next = new PasswordField(), repeat = new PasswordField();
    private final ListView<String> history = new ListView<>();
    private final ListView<AccountProfile> directory = new ListView<>();
    private final boolean admin;
    private boolean loading, pendingRefresh, busy, profileShown;
    private String uncertainAction, uncertainRef;
    private long uncertainAmount;

    AccountDialog(Window owner, AuctionClient client, String name, String role) {
        this.client = client;
        admin = role.equals("ADMIN");
        dialog.initOwner(owner); dialog.setTitle("Tài khoản & ví");
        dialog.setHeaderText(name + "   /   " + Display.role(role));
        var pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/css/auction.css").toExternalForm());
        pane.getButtonTypes().add(new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
        addTab("Ví đấu giá", walletTab()); addTab("Hồ sơ", profileTab());
        if (admin) addTab("Quản lý tài khoản", accountsTab());
        status.setWrapText(true); status.setId("accountStatus");
        pane.setContent(new VBox(10, tabs,status)); pane.setPrefWidth(610);
    }
    private void addTab(String name, VBox body) {
        body.setStyle("-fx-padding: 14;");
        var tab = new Tab(name, body); tab.setClosable(false); tabs.getTabs().add(tab);
    }
    private VBox walletTab() {
        total.setId("walletBalance"); available.setId("walletAvailable"); amount.setId("walletAmount");
        history.setPrefHeight(155); history.setPlaceholder(new Label("Chưa có giao dịch ví."));
        amount.setPromptText("Số tiền nguyên (VND)");
        var add = button("Nạp tiền", () -> move(true)); add.setId("deposit");
        add.getStyleClass().add("primary");
        var withdraw = button("Rút tiền", () -> move(false)); withdraw.setId("withdraw");
        var box = new VBox(6, total, held, available); box.getStyleClass().add("quote");
        return new VBox(10, new Label("Ví mô phỏng · VND"), box, label("Số tiền giao dịch",amount),
                new HBox(8,amount,add,withdraw), new Label("100 giao dịch gần nhất"),history);
    }
    private VBox profileTab() {
        display.setPromptText("Tên hiển thị");
        var save = button("Lưu tên hiển thị", () -> mutate("rename", fields("display",display.getText()),"Đã lưu tên hiển thị."));
        var change = button("Đổi mật khẩu", () -> {
            if (!next.getText().equals(repeat.getText())) { status.setText("Hai mật khẩu mới chưa khớp."); return; }
            mutate("password",fields("current",current.getText(),"next",next.getText()),"Đã đổi mật khẩu và đăng xuất các phiên khác.");
        });
        return new VBox(8, label("Tên hiển thị",display),display,save,new Separator(),label("Mật khẩu hiện tại",current),
                current,label("Mật khẩu mới (8–128 ký tự)",next),next,label("Nhập lại mật khẩu mới",repeat),repeat,change);
    }
    private VBox accountsTab() {
        directory.setPrefHeight(240);
        directory.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(AccountProfile p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.name() + (p.displayName().isBlank() ? "" : " · " + p.displayName())
                        + "  /  " + Display.role(p.role().name()) + "  /  " + (p.locked() ? "Đã khóa" : "Hoạt động"));
            }
        });
        var toggle = button("Khóa / mở khóa", () -> {
            var p = directory.getSelectionModel().getSelectedItem();
            if (p == null || p.role() == User.Role.ADMIN) { status.setText("Chọn tài khoản người mua hoặc người bán."); return; }
            String action = p.locked() ? "Mở khóa" : "Khóa";
            if (confirm(action + " tài khoản " + p.name() + "?"))
                mutate("lockAccount",fields("target",p.name(),"locked",!p.locked()),"Đã cập nhật trạng thái tài khoản.");
        });
        return new VBox(8,new Label("Danh sách người dùng"),directory,toggle);
    }
    private boolean confirm(String question) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK,ButtonType.CANCEL);
        alert.initOwner(dialog.getDialogPane().getScene().getWindow()); alert.setHeaderText("Xác nhận giao dịch");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private static Label label(String text, Control input) { var l = new Label(text); l.setLabelFor(input); return l; }
    private static Button button(String text, Runnable fn) { var b = new Button(text); b.setOnAction(e -> fn.run()); return b; }
    void show() { dialog.show(); refresh(); }
    void close() { dialog.close(); }
    boolean showing() { return dialog.isShowing(); }

    void refresh() {
        if (!showing()) return;
        if (loading || busy) { pendingRefresh = true; return; }
        loading = true;
        Thread.startVirtualThread(() -> {
            try {
                var wallet = Wire.JSON.fromJson(client.call("wallet",fields()),Wallet.class);
                var profile = Wire.JSON.fromJson(client.call("profile",fields()),AccountProfile.class);
                var accounts = admin ? Wire.JSON.fromJson(client.call("accounts",fields()),AccountProfile[].class) : null;
                Platform.runLater(() -> {
                    if (!showing()) return;
                    total.setText("Số dư ví     " + Display.price(wallet.balance()));
                    held.setText("Đang giữ     " + Display.price(wallet.held()));
                    available.setText("Khả dụng     " + Display.price(wallet.available()));
                    history.getItems().setAll(wallet.recent().stream().map(e -> Display.time(e.time()) + "   "
                            + (e.delta() > 0 ? "+" : "") + Display.price(e.delta()) + "   ·   " + e.label()).toList());
                    if (!profileShown) { display.setText(profile.displayName()); profileShown = true; }
                    if (accounts != null) {
                        var was = directory.getSelectionModel().getSelectedItem();
                        directory.getItems().setAll(accounts);
                        if (was != null) directory.getItems().stream().filter(p -> p.name().equals(was.name())).findFirst()
                                .ifPresent(p -> directory.getSelectionModel().select(p));
                    }
                });
            } catch (Exception e) { Platform.runLater(() -> status.setText("Không tải được tài khoản: " + e.getMessage())); }
            finally { Platform.runLater(() -> { loading=false; if (pendingRefresh) { pendingRefresh=false; refresh(); } }); }
        });
    }
    private void move(boolean deposit) {
        final long value;
        try { value = Long.parseLong(amount.getText().strip()); if (value <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { status.setText("Nhập số tiền nguyên dương bằng đồng."); return; }
        String action = deposit ? "deposit" : "withdraw";
        if (!deposit && !confirm("Rút " + Display.price(value) + " khỏi ví mô phỏng?")) return;
        String ref = action.equals(uncertainAction) && value == uncertainAmount ? uncertainRef : UUID.randomUUID().toString();
        mutate(action, fields("amount",value,"requestId",ref),deposit ? "Đã nạp tiền vào ví." : "Đã rút tiền khỏi ví.");
    }
    private void mutate(String action, JsonObject data, String success) {
        if (busy) return;
        busy=true; tabs.setDisable(true); status.setText("Đang xử lý…");
        Thread.startVirtualThread(() -> {
            try {
                client.call(action,data);
                Platform.runLater(() -> {
                    status.setText(success);
                    if (data.has("requestId")) { uncertainAction=null; amount.clear(); }
                    if (action.equals("password")) { current.clear(); next.clear(); repeat.clear(); }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (e instanceof TimeoutException && data.has("requestId")) {
                        uncertainAction=action; uncertainAmount=data.get("amount").getAsLong(); uncertainRef=data.get("requestId").getAsString();
                        status.setText("Chưa có phản hồi. Kiểm tra lịch sử. Thử lại cùng số tiền sẽ dùng lại mã giao dịch.");
                    } else status.setText("Không thực hiện được: " + e.getMessage());
                });
            } finally { Platform.runLater(() -> { busy=false; tabs.setDisable(false); refresh(); }); }
        });
    }
}
