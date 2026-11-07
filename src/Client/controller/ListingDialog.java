package Client.controller;

import Client.state.Display;
import Shared.model.*;
import Shared.protocol.Wire;
import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.Optional;

final class ListingDialog {
    static Optional<JsonObject> show(Window owner, Auction a) {
        var dialog = new Dialog<JsonObject>(); dialog.initOwner(owner);
        dialog.setTitle(a == null ? "Đăng sản phẩm" : "Sửa sản phẩm");
        dialog.setHeaderText("Thông tin phiên đấu");
        var pane = dialog.getDialogPane();
        pane.getStylesheets().add(ListingDialog.class.getResource("/css/auction.css").toExternalForm());
        var save = new ButtonType("Lưu sản phẩm", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(save, ButtonType.CANCEL);
        var name = new TextField(a == null ? "" : a.name());
        var description = new TextArea(a == null ? "" : a.description()); description.setPrefRowCount(3); description.setWrapText(true);
        var category = new ChoiceBox<Item.Category>(); category.getItems().setAll(Item.Category.values());
        category.setValue(a == null ? Item.Category.ART : a.category());
        var opening = new TextField(a == null ? "" : Long.toString(a.opening()));
        long now = System.currentTimeMillis();
        var start = new TextField(Display.time(a == null ? now : a.start()));
        var end = new TextField(Display.time(a == null ? now + 3_600_000 : a.end()));
        var error = new Label(); error.setWrapText(true); error.getStyleClass().add("feedback");
        var photo = new javafx.beans.property.SimpleStringProperty();
        var photoStatus = new Label(a != null && a.photoVersion() != null ? "Đang giữ ảnh hiện tại" : "Dùng tranh minh họa phân loại");
        var choose = new Button("Chọn ảnh…");
        var discard = new Button("Bỏ ảnh");
        choose.setOnAction(e -> {
            var picker = new javafx.stage.FileChooser(); picker.setTitle("Ảnh sản phẩm");
            picker.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JPEG / PNG", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
            var file = picker.showOpenDialog(owner);
            if (file == null) return;
            choose.setDisable(true); discard.setDisable(true); pane.lookupButton(save).setDisable(true);
            Thread.startVirtualThread(() -> {
                try {
                    var encoded = ProductPhoto.prepare(file);
                    javafx.application.Platform.runLater(() -> { photo.set(encoded); photoStatus.setText(file.getName()); error.setText(""); });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> error.setText("Không đọc được ảnh. Chọn tệp JPEG/PNG tối đa 16 MB."));
                } finally {
                    javafx.application.Platform.runLater(() -> { choose.setDisable(false); discard.setDisable(false); pane.lookupButton(save).setDisable(false); });
                }
            });
        });
        discard.setOnAction(e -> { photo.set(""); photoStatus.setText("Dùng tranh minh họa phân loại"); });
        var form = new GridPane(); form.setHgap(12); form.setVgap(10);
        String[] labels = {"Tên sản phẩm", "Mô tả", "Phân loại", "Giá khởi điểm (₫)", "Bắt đầu", "Kết thúc"};
        Control[] controls = {name, description, category, opening, start, end};
        for (int i = 0; i < controls.length; i++) {
            var label = new Label(labels[i]); label.setLabelFor(controls[i]); form.addRow(i, label, controls[i]);
        }
        form.addRow(6, new Label("Ảnh sản phẩm"), new VBox(6, new HBox(8, choose, discard), photoStatus));
        pane.setContent(new VBox(12, form, new Label("Giờ địa phương · yyyy-MM-dd HH:mm:ss"), error));
        var data = new JsonObject();
        pane.lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                long price = Long.parseLong(opening.getText().strip());
                long from = Display.parseTime(start.getText()), until = Display.parseTime(end.getText());
                if (name.getText().isBlank() || description.getText().isBlank() || price <= 0 || until <= Math.max(from, System.currentTimeMillis()))
                    throw new IllegalArgumentException();
                var fields = Wire.fields("name", name.getText(), "description", description.getText(), "category", category.getValue(),
                        "opening", price, "start", from, "end", until);
                fields.entrySet().forEach(e -> data.add(e.getKey(), e.getValue()));
                if (a != null) data.addProperty("id", a.id());
                if (photo.get() != null) data.addProperty("photo", photo.get());
            } catch (RuntimeException ex) {
                error.setText("Kiểm tra tên, mô tả, giá nguyên dương và thời gian kết thúc."); event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save ? data : null);
        return dialog.showAndWait();
    }
}
