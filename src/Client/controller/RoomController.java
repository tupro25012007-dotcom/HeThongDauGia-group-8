package Client.controller;

import Client.MainFx;
import Client.network.AuctionClient;
import Client.state.Display;
import Shared.model.*;
import Shared.protocol.Wire;
import com.google.gson.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.*;
import java.util.*;
import java.util.function.Function;

public final class RoomController {
    @FXML private Label identity, count, title, meta, description, price, leader, times, clockLabel, notice;
    @FXML private TableView<Auction> table;
    @FXML private ComboBox<String> filter, category;
    @FXML private CheckBox own, savedOnly;
    @FXML private Label liveCount, watchCount, pictureCaption;
    @FXML private Button follow, bidButton, zoom;
    @FXML private StackPane pictureFrame;
    @FXML private ImageView picture;
    @FXML private TextField search, amount, maximum, increment;
    @FXML private VBox bidBox;
    @FXML private Button create, edit, remove, finish, cancel, pay, export;
    @FXML private LineChart<Number,Number> chart;
    @FXML private NumberAxis timeAxis;
    @FXML private ListView<String> history;
    private List<Auction> lots = List.of();
    private Set<String> watched = Set.of();
    private final Map<Item.Category,Image> prints = new EnumMap<>(Item.Category.class);
    private String displayedCover;
    private boolean supportsWatching = true;
    private Auction selected;
    private String name, role;
    private MainFx app;
    private AuctionClient client;
    private Timeline timer;
    private AccountDialog account;
    private boolean loading, again, stopped, submitting, rendering;

    public void start(MainFx app, AuctionClient client, String name, String role) {
        this.app = app; this.client = client; this.name = name; this.role = role;
        identity.setText(name + "   /   " + Display.role(role));
        picture.fitWidthProperty().bind(pictureFrame.widthProperty().subtract(2));
        picture.fitHeightProperty().bind(pictureFrame.heightProperty().subtract(2));
        category.getItems().add("Mọi phân loại");
        for (var c : Item.Category.values()) category.getItems().add(c.toString());
        category.getSelectionModel().selectFirst();
        filter.getItems().add("Mọi trạng thái");
        for (var state : Auction.Status.values()) filter.getItems().add(state.toString());
        filter.getSelectionModel().selectFirst();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("Sản phẩm", Auction::name, 152); column("Giá hiện tại", Auction::price, 100).setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Long v, boolean empty) { super.updateItem(v, empty); setText(empty || v == null ? null : Display.price(v)); }
        });
        column("Trạng thái", Auction::status, 98);
        table.setPlaceholder(new Label("Chưa có phiên phù hợp."));
        history.setPlaceholder(new Label("Chưa có lượt trả giá."));
        table.getSelectionModel().selectedItemProperty().addListener((o, before, after) -> { if (!rendering) show(after); });
        search.textProperty().addListener((o,b,a) -> render());
        filter.setOnAction(e -> render()); own.setOnAction(e -> render());
        savedOnly.setOnAction(e -> render()); category.setOnAction(e -> render());
        visible(create, role.equals("SELLER"));
        timeAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return Display.time(value.longValue()).substring(11); }
            @Override public Number fromString(String text) { throw new UnsupportedOperationException(); }
        });
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> countdown()));
        timer.setCycleCount(Animation.INDEFINITE); timer.play();
        show(null); notice.setText("Đang tải danh mục…"); refresh();
    }
    private <T> TableColumn<Auction,T> column(String heading, Function<Auction,T> read, int width) {
        var col = new TableColumn<Auction,T>(heading);
        col.setPrefWidth(width); col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(read.apply(c.getValue())));
        table.getColumns().add(col); return col;
    }
    public void stop() { stopped = true; if (timer != null) timer.stop(); if (account != null) account.close(); }
    @FXML private void openAccount() {
        if (account != null && account.showing()) return;
        account = new AccountDialog(table.getScene().getWindow(),client,name,role);
        account.show();
    }
    public void refresh() {
        if (stopped) return;
        if (account != null && account.showing()) account.refresh();
        if (loading) { again = true; return; }
        loading = true;
        Thread.startVirtualThread(() -> {
            try {
                var result = client.call("list", new JsonObject());
                var data = Arrays.asList(Wire.JSON.fromJson(result, Auction[].class));
                Set<String> saved = Set.of();
                boolean available = supportsWatching;
                if (available) {
                    try {
                        saved = Set.of(Wire.JSON.fromJson(client.call("watched", new JsonObject()), String[].class));
                    } catch (IllegalArgumentException e) {
                        if (!"Lệnh không được hỗ trợ.".equals(e.getMessage())) throw e;
                        available = false;
                    }
                }
                var savedItems = saved;
                boolean watchAvailable = available;
                Platform.runLater(() -> {
                    if (stopped) return;
                    lots = data; watched = savedItems; supportsWatching = watchAvailable;
                    savedOnly.setDisable(!watchAvailable); render();
                    if (!watchAvailable) notice.setText("Khởi động lại server bằng bản mới để sử dụng theo dõi và ảnh sản phẩm.");
                    else if (notice.getText().equals("Đang tải danh mục…")) notice.setText("Đã kết nối · Giá cập nhật trực tiếp");
                });
            } catch (Exception e) { Platform.runLater(() -> notice.setText("Không tải được danh mục: " + e.getMessage())); }
            finally { Platform.runLater(() -> { loading = false; if (again) { again = false; refresh(); } }); }
        });
    }
    private void render() {
        String key = search.getText().strip().toLowerCase(Locale.ROOT);
        String id = selected == null ? null : selected.id();
        var rows = lots.stream().filter(a -> (a.name() + " " + a.seller()).toLowerCase(Locale.ROOT).contains(key))
                .filter(a -> filter.getSelectionModel().getSelectedIndex() <= 0 || a.status().toString().equals(filter.getValue()))
                .filter(a -> category.getSelectionModel().getSelectedIndex() <= 0 || a.category().toString().equals(category.getValue()))
                .filter(a -> !savedOnly.isSelected() || watched.contains(a.id()))
                .filter(a -> !own.isSelected() || a.seller().equals(name) || a.bids().stream().anyMatch(b -> b.bidder().equals(name))).toList();
        rendering = true;
        table.getItems().setAll(rows); table.sort();
        var next = table.getItems().stream().filter(a -> a.id().equals(id)).findFirst().orElse(table.getItems().isEmpty() ? null : table.getItems().getFirst());
        table.getSelectionModel().select(next); rendering = false;
        show(next); count.setText(rows.size() + " PHIÊN   /   " + lots.size() + " TRONG DANH MỤC");
        liveCount.setText(lots.stream().filter(a -> a.status() == Auction.Status.RUNNING).count() + " đang đấu");
        watchCount.setText(watched.size() + " đang theo dõi");
    }
    private void show(Auction a) {
        if (selected == null || a == null || !selected.id().equals(a.id())) {
            amount.clear(); maximum.clear(); increment.clear();
        }
        selected = a;
        zoom.setDisable(a == null);
        showPicture(a);
        follow.setDisable(a == null || !supportsWatching);
        follow.setText(a != null && watched.contains(a.id()) ? "Bỏ theo dõi" : "Theo dõi phiên");
        bidButton.setDisable(a == null || name.equals(a.leader()));
        export.setDisable(a == null);
        boolean running = a != null && a.status() == Auction.Status.RUNNING;
        boolean editable = a != null && a.seller().equals(name) && a.bids().isEmpty() && (running || a.status() == Auction.Status.OPEN);
        visible(edit, editable); visible(remove, editable);
        visible(finish, role.equals("ADMIN") && running);
        visible(cancel, role.equals("ADMIN") && a != null && a.status() != Auction.Status.CANCELED && a.status() != Auction.Status.PAID);
        visible(pay, a != null && name.equals(a.leader()) && a.status() == Auction.Status.FINISHED);
        visible(bidBox, running && role.equals("BIDDER"));
        title.setText(a == null ? "Chọn một phiên đấu" : a.name());
        meta.setText(a == null ? "" : a.category() + "   /   " + a.status() + "   /   @" + a.seller());
        description.setText(a == null ? "" : a.description());
        price.setText(a == null ? "—" : Display.price(a.price()));
        boolean ended = a != null && (a.status() == Auction.Status.FINISHED || a.status() == Auction.Status.PAID);
        leader.setText(a == null ? "" : a.status() == Auction.Status.CANCELED ? "Phiên đã hủy" :
                a.leader() == null ? (ended ? "Không có người thắng" : "Chưa có người đặt giá") :
                        (ended ? "Người thắng: " : "Đang dẫn đầu: ") + a.leader());
        times.setText(a == null ? "" : "Mở   " + Display.time(a.start()) + "\nChốt   " + Display.time(a.end()));
        countdown(); history.getItems().clear(); chart.getData().clear();
        if (a == null) return;
        long last = a.bids().isEmpty() ? a.start() : a.bids().getLast().time();
        long span = Math.max(1000, last - a.start());
        timeAxis.setAutoRanging(false);
        timeAxis.setLowerBound(a.start()); timeAxis.setUpperBound(a.start() + (double)span);
        timeAxis.setTickUnit(Math.max(1000, Math.ceil(span / 3.0)));
        var series = new XYChart.Series<Number,Number>();
        series.getData().add(new XYChart.Data<>(a.start(), a.opening()));
        for (var b : a.bids()) series.getData().add(new XYChart.Data<>(b.time(), b.amount()));
        chart.getData().add(series);
        for (var b : a.bids().reversed()) history.getItems().add(Display.time(b.time()).substring(11) + "   " + b.bidder() + "   ·   "
                + Display.price(b.amount()) + (b.automatic() ? "   ·   Tự động" : ""));
    }
    private void countdown() {
        if (selected == null) { clockLabel.setText(""); return; }
        var s = selected.status();
        if (s != Auction.Status.OPEN && s != Auction.Status.RUNNING) { clockLabel.setText(s.toString()); return; }
        long secs = Math.max(0, ((s == Auction.Status.OPEN ? selected.start() : selected.end()) - System.currentTimeMillis() + 999) / 1000);
        clockLabel.setText((s == Auction.Status.OPEN ? "Mở sau " : "Chốt sau ") + "%02d:%02d:%02d".formatted(secs/3600, secs/60%60, secs%60));
    }
    private static void visible(Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
    private void showPicture(Auction a) {
        if (a == null) {
            displayedCover = null; picture.setImage(null); pictureCaption.setText("Chọn phiên để xem hình."); return;
        }
        String key = a.id() + "/" + a.category().name() + "/" + a.photoVersion();
        if (key.equals(displayedCover)) return;
        displayedCover = key;
        picture.setImage(prints.computeIfAbsent(a.category(), c -> new Image(getClass().getResource(
                "/gallery/" + c.name().toLowerCase(Locale.ROOT) + ".png").toExternalForm())));
        String caption = "Minh họa phân loại  ·  " + a.category();
        pictureCaption.setText(caption); picture.setAccessibleText(caption);
        if (a.photoVersion() == null) return;
        pictureCaption.setText("Đang tải ảnh người bán…");
        Thread.startVirtualThread(() -> {
            try {
                var data = client.call("photo", Wire.fields("id", a.id())).getAsString();
                var image = new Image(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data)));
                if (image.isError()) throw new IllegalArgumentException();
                Platform.runLater(() -> {
                    if (stopped || !key.equals(displayedCover)) return;
                    picture.setImage(image); pictureCaption.setText("Ảnh người bán  ·  " + a.name());
                    picture.setAccessibleText(pictureCaption.getText());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!key.equals(displayedCover)) return;
                    displayedCover = null; pictureCaption.setText("Chưa tải được ảnh người bán  ·  " + caption);
                });
            }
        });
    }
    @FXML private void toggleWatch() {
        if (selected == null) return;
        boolean enable = !watched.contains(selected.id());
        send("watch", Wire.fields("id", selected.id(), "enabled", enable), enable ? "Đã thêm vào các phiên theo dõi." : "Đã bỏ theo dõi phiên.");
    }
    @FXML private void openPicture() {
        if (selected == null || picture.getImage() == null) return;
        var dialog = new Dialog<Void>();
        dialog.initOwner(table.getScene().getWindow()); dialog.setTitle(selected.name());
        dialog.setHeaderText(pictureCaption.getText());
        var full = new ImageView(picture.getImage()); full.setPreserveRatio(true);
        full.setFitWidth(Math.min(880, table.getScene().getWidth() * .8));
        full.setFitHeight(Math.min(560, table.getScene().getHeight() * .7));
        full.setAccessibleText(picture.getAccessibleText());
        var pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/css/auction.css").toExternalForm());
        pane.setContent(new StackPane(full)); pane.getButtonTypes().add(new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }
    @FXML private void suggestBid(javafx.event.ActionEvent e) {
        if (selected == null) return;
        long step = Long.parseLong(((Button)e.getSource()).getUserData().toString());
        amount.setText(Long.toString(selected.price() + step)); amount.requestFocus();
    }
    private void send(String action, JsonObject data, String success) {
        if (submitting) return;
        submitting = true; notice.setText("Đang gửi yêu cầu…");
        Thread.startVirtualThread(() -> {
            try { client.call(action, data); Platform.runLater(() -> { notice.setText(success); refresh(); }); }
            catch (Exception e) { Platform.runLater(() -> notice.setText(e instanceof java.util.concurrent.TimeoutException
                    ? "Chưa nhận được phản hồi. Kiểm tra lịch sử trước khi gửi lại." : "Không thực hiện được: " + e.getMessage())); }
            finally { Platform.runLater(() -> submitting = false); }
        });
    }
    @FXML private void placeBid() { offer(false); }
    @FXML private void autoBid() { offer(true); }
    @FXML private void viewRule() {
        Auction a = selected; if (a == null) return;
        Thread.startVirtualThread(() -> {
            try {
                var rule = client.call("ownRule", Wire.fields("id", a.id()));
                Platform.runLater(() -> {
                    if (stopped || selected == null || !selected.id().equals(a.id())) return;
                    if (rule == null || rule.isJsonNull()) { notice.setText("Bạn chưa đặt quy tắc tự động cho phiên này."); return; }
                    var data = rule.getAsJsonObject();
                    maximum.setText(data.get("maximum").getAsString()); increment.setText(data.get("increment").getAsString());
                    notice.setText("Đã tải quy tắc tự động của bạn.");
                });
            } catch (Exception e) { Platform.runLater(() -> notice.setText("Không tải được quy tắc tự động.")); }
        });
    }
    @FXML private void disableAuto() { confirm("disableAuto", "Tắt đấu giá tự động cho phiên"); }
    private void offer(boolean auto) {
        if (selected == null) return;
        try {
            var data = Wire.fields("id", selected.id());
            if (auto) {
                data.addProperty("maximum", Long.parseLong(maximum.getText().strip()));
                data.addProperty("increment", Long.parseLong(increment.getText().strip()));
            } else data.addProperty("amount", Long.parseLong(amount.getText().strip()));
            send(auto ? "auto" : "bid", data, auto ? "Đã lưu quy tắc tự động." : "Giá đặt đã được ghi nhận.");
        } catch (NumberFormatException e) { notice.setText("Nhập số tiền nguyên bằng đồng, không thêm dấu phân cách."); }
    }
    @FXML private void createLot() { listing(null); }
    @FXML private void editLot() { listing(selected); }
    private void listing(Auction a) {
        ListingDialog.show(table.getScene().getWindow(), a).ifPresent(data -> send("publish", data, "Đã lưu sản phẩm."));
    }
    private void confirm(String action, String text) {
        Auction a = selected; if (a == null || submitting) return;
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, text + " «" + a.name() + "»?", ButtonType.OK, ButtonType.CANCEL);
        dialog.initOwner(table.getScene().getWindow()); dialog.setHeaderText(text);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/auction.css").toExternalForm());
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
            send(action, Wire.fields("id", a.id()), "Đã cập nhật phiên.");
    }
    @FXML private void deleteLot() { confirm("delete", "Xóa sản phẩm"); }
    @FXML private void finishLot() { confirm("finish", "Đóng phiên và chốt kết quả"); }
    @FXML private void cancelLot() { confirm("cancel", "Hủy phiên đấu"); }
    @FXML private void payLot() {
        if (selected != null) confirm("pay", "Thanh toán " + Display.price(selected.price()) + " từ ví cho phiên");
    }
    @FXML private void exportHistory() {
        Auction snapshot = selected;
        if (snapshot == null) return;
        var picker = new javafx.stage.FileChooser();
        picker.setTitle("Xuất lịch sử đặt giá");
        picker.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        picker.setInitialFileName("phien-" + snapshot.id() + ".json");
        var file = picker.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;
        try {
            java.nio.file.Files.writeString(file.toPath(), Wire.JSON.toJson(snapshot));
            notice.setText("Đã xuất lịch sử đến " + file.getName());
        } catch (java.io.IOException e) { notice.setText("Không thể ghi tệp: " + e.getMessage()); }
    }
    @FXML private void logout() { app.login(""); }
    @FXML private void guide() { GuideVideo.open(table.getScene().getWindow()); }
}
