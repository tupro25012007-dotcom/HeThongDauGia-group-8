package Client;

import Client.view.AuctionView;
import Server.MainServer;

import javax.swing.*;
import java.net.Socket;

public class MainFx {
    public static void main(String[] args) {
        if (!isServerRunning()) {
            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    MainServer.startServer();
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {}
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new AuctionView().setVisible(true);
            }
        });
    }

    private static boolean isServerRunning() {
        try (Socket s = new Socket("localhost", MainServer.PORT)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
