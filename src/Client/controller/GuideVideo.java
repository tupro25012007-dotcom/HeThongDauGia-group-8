package Client.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import java.awt.Desktop;
import java.io.*;
import java.nio.file.*;

final class GuideVideo {
    private static Path copy;
    static void open(Window owner) {
        Thread.startVirtualThread(() -> {
            try {
                Desktop.getDesktop().open(file().toFile());
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    var warning = new Alert(Alert.AlertType.INFORMATION);
                    warning.initOwner(owner); warning.setHeaderText("Mở video hướng dẫn");
                    warning.setContentText("Mở tệp phien-08.mp4 trong thư mục tài nguyên guide bằng trình phát video trên máy.");
                    warning.show();
                });
            }
        });
    }
    private static synchronized Path file() throws IOException {
        if (copy != null && Files.exists(copy)) return copy;
        try (var source = GuideVideo.class.getResourceAsStream("/guide/phien-08.mp4")) {
            if (source == null) throw new FileNotFoundException();
            Path path = Files.createTempFile("phien-08-", ".mp4");
            path.toFile().deleteOnExit();
            Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
            copy = path; return path;
        }
    }
}
