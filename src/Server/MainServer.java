package Server;

import Server.network.ClientHandler;
import Server.repository.AuctionRepository;
import Server.service.AuctionService;
import Shared.model.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainServer {
    public static final int PORT = 8080;
    private static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<ClientHandler>();
    private static AuctionService auctionService = new AuctionService(new AuctionRepository());

    public static void main(String[] args) {
        startServer();
    }

    public static AuctionService getAuctionService() {
        return auctionService;
    }

    public static void startServer() {
        System.out.println("=== SERVER ĐẤU GIÁ V3 ĐANG CHẠY TẠI CỔNG " + PORT + " ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, auctionService);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("Server đã được khởi chạy trước đó trên cổng " + PORT);
        }
    }

    public static void broadcast(Message message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}
