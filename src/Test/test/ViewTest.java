package test;

import Client.MainFx;
import Client.network.AuctionClient;
import Server.network.AuctionServer;
import Server.repository.AuctionStore;
import Server.service.AuctionManager.Listing;
import Shared.model.*;
import Shared.protocol.Wire;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="test.fx", matches="true")
class ViewTest {
    private static <T> T fx(Callable<T> action) throws Exception {
        var work = new FutureTask<>(action);
        Platform.runLater(work); return work.get(10, TimeUnit.SECONDS);
    }
    private static void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() >= end) fail("JavaFX state did not update");
            Thread.sleep(35);
        }
    }
    @Test void loginBidAndSeeAnotherClientsOffer() throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var app = new MainFx();
        String previousPort = System.getProperty("auction.port");
        try (var store = new AuctionStore("jdbc:sqlite::memory:");
             var server = new AuctionServer(store, "127.0.0.1", 0)) {
            Thread.startVirtualThread(() -> { try { server.serve(); } catch (Exception e) { throw new RuntimeException(e); } });
            var seller = server.users.create("nhaban", "password123", User.Role.SELLER);
            server.users.register("nguoimua", "password123", User.Role.BIDDER);
            long now = System.currentTimeMillis();
            var a = server.manager.publish(seller, null, new Listing("Phố cổ sau mưa", "Tranh màu nước trên giấy cotton, 30 × 40 cm.", Item.Category.ART, 650000, now, now + 3600000));
            var testImage = Path.of(getClass().getResource("/gallery/electronics.png").toURI()).toFile();
            var camera = server.manager.publish(seller, null, new Listing("Máy ảnh Fujifilm X-T30", "Máy ảnh kèm ống kính.",
                    Item.Category.ELECTRONICS, 8500000, now, now + 7200000), ProductPhoto.prepare(testImage));
            server.manager.publish(seller, null, new Listing("Xe đạp touring cổ điển", "Khung thép, bánh 700C.", Item.Category.VEHICLE, 2400000, now + 600000, now + 7200000));
            System.setProperty("auction.port", String.valueOf(server.port()));
            Stage window = fx(() -> { var stage = new Stage(); app.start(stage); return stage; });
            var scene = fx(window::getScene);
            fx(() -> {
                preview(scene, "Login");
                ((TextField)scene.lookup("#name")).setText("nguoimua");
                ((PasswordField)scene.lookup("#password")).setText("password123");
                ((TextField)scene.lookup("#host")).clear();
                ((TextField)scene.lookup("#port")).setText("70000");
                ((Button)scene.lookup("#submit")).fire();
                assertTrue(((Label)scene.lookup("#feedback")).getText().contains("65535"));
                ((TextField)scene.lookup("#host")).clear();
                ((TextField)scene.lookup("#port")).clear();
                ((Button)scene.lookup("#submit")).fire();
                assertEquals("127.0.0.1", ((TextField)scene.lookup("#host")).getText());
                assertEquals(String.valueOf(server.port()), ((TextField)scene.lookup("#port")).getText());
                return null;
            });
            until(() -> scene.lookup("#title") instanceof Label label && label.getText().equals(a.name()));
            fx(() -> { ((Button)scene.lookup("#accountButton")).fire(); return null; });
            var walletScene = fx(() -> javafx.stage.Window.getWindows().stream().map(javafx.stage.Window::getScene)
                    .filter(s -> s != null && s.lookup("#walletAmount") != null).findFirst().orElseThrow());
            fx(() -> {
                ((TextField)walletScene.lookup("#walletAmount")).setText("1000000");
                ((Button)walletScene.lookup("#deposit")).fire(); return null;
            });
            until(() -> ((Label)walletScene.lookup("#walletAvailable")).getText().contains("1.000.000"));
            fx(() -> {
                preview(walletScene, "Wallet");
                var dialogPane = (DialogPane)walletScene.getRoot();
                ((Button)dialogPane.lookupButton(dialogPane.getButtonTypes().getFirst())).fire(); return null;
            });
            fx(() -> {
                ((ComboBox<?>)scene.lookup("#category")).getSelectionModel().select(1);
                assertEquals(1, ((TableView<?>)scene.lookup("#table")).getItems().size());
                assertEquals(camera.name(), ((Label)scene.lookup("#title")).getText()); return null;
            });
            until(() -> ((Label)scene.lookup("#pictureCaption")).getText().startsWith("Ảnh người bán"));
            fx(() -> {
                ((ComboBox<?>)scene.lookup("#category")).getSelectionModel().selectFirst();
                ((TableView<?>)scene.lookup("#table")).getSelectionModel().selectFirst(); return null;
            });
            fx(() -> {
                assertNotNull(((ImageView)scene.lookup("#picture")).getImage());
                preview(scene, "Catalogue");
                ((Button)scene.lookup("#follow")).fire(); return null;
            });
            until(() -> ((Button)scene.lookup("#follow")).getText().equals("Bỏ theo dõi"));
            fx(() -> {
                ((CheckBox)scene.lookup("#savedOnly")).fire();
                assertEquals(1, ((TableView<?>)scene.lookup("#table")).getItems().size());
                ((CheckBox)scene.lookup("#savedOnly")).fire();
                var quick = scene.getRoot().lookupAll(".suggestion").stream().map(Button.class::cast)
                        .filter(button -> "50000".equals(button.getUserData())).findFirst().orElseThrow();
                quick.fire();
                var field = (TextField)scene.lookup("#amount");
                assertEquals("700000", field.getText());
                preview(scene, "Offer");
                field.fireEvent(new javafx.event.ActionEvent()); return null;
            });
            until(() -> ((Label)scene.lookup("#leader")).getText().contains("nguoimua"));
            fx(() -> { preview(scene, "Leading"); return null; });
            try (var rival = new AuctionClient("127.0.0.1", server.port(), () -> {}, () -> {})) {
                rival.call("register", Wire.fields("name", "minhanh", "password", "password123", "role", "BIDDER"));
                rival.call("deposit", Wire.fields("amount", 750000, "requestId", java.util.UUID.randomUUID().toString()));
                rival.call("bid", Wire.fields("id", a.id(), "amount", 750000));
                until(() -> ((Label)scene.lookup("#leader")).getText().contains("minhanh"));
                assertEquals(2, fx(() -> ((ListView<?>)scene.lookup("#history")).getItems().size()));
                fx(() -> { preview(scene, "Room"); return null; });
                fx(() -> {
                    ((ScrollPane)scene.lookup("#detailScroll")).setVvalue(1);
                    preview(scene, "History"); return null;
                });
            } finally { fx(() -> { app.stop(); window.hide(); return null; }); }
        } finally {
            if (previousPort == null) System.clearProperty("auction.port");
            else System.setProperty("auction.port", previousPort);
            Platform.exit();
        }
    }
    private static void preview(Scene scene, String name) throws Exception {
        WritableImage image = scene.snapshot(null);
        var pixels = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        image.getPixelReader().getPixels(0,0,pixels.getWidth(),pixels.getHeight(), PixelFormat.getIntArgbInstance(),
                ((java.awt.image.DataBufferInt)pixels.getRaster().getDataBuffer()).getData(),0,pixels.getWidth());
        ImageIO.write(pixels, "png", Path.of("target", name + "-preview.png").toFile());
    }
}
