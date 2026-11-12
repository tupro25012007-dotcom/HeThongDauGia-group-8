package Client;
import javafx.animation.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
public class MainFx extends Application {
    private static Stage mainStage;
    @Override
    public void start(Stage stage) throws Exception {
        mainStage = stage;
        showLogin();
        stage.setTitle("Online Auction System");
        stage.show();
    }
    public static void showLogin() throws Exception {
        mainStage.setScene(new Scene(FXMLLoader.load(MainFx.class.getResource("/fxml/login.fxml"))));
    }
    public static void showRegister() throws Exception {
        mainStage.setScene(new Scene(FXMLLoader.load(MainFx.class.getResource("/fxml/register.fxml"))));
    }
    public static void showHome(String username) {
        javafx.scene.control.Label message = new javafx.scene.control.Label("Xin chào " + username + "!");
        javafx.scene.control.Button logout = new javafx.scene.control.Button("Đăng xuất");
        logout.setOnAction(event -> {
            try {
                showLogin();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(15, message, logout);
        root.setStyle("")
    }

}