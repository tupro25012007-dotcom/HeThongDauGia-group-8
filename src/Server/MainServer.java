package Server;

import Server.network.AuctionServer;
import Server.repository.AuctionStore;
import Server.service.AuctionManager.Listing;
import Shared.model.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.Arrays;

public final class MainServer {
    public static void main(String[] args) throws Exception {
        boolean demo = Arrays.asList(args).contains("--demo");
        Path file = Path.of(System.getProperty("auction.db", demo ? "data/demo.sqlite" : "data/auction.sqlite")).toAbsolutePath();
        Files.createDirectories(file.getParent());
        String host = System.getProperty("auction.host", "127.0.0.1");
        int port = Integer.getInteger("auction.port", 5050);
        try (var guard = FileChannel.open(Path.of(file + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = guard.tryLock()) {
            if (lock == null) throw new IllegalStateException("Cơ sở dữ liệu đang được máy chủ khác sử dụng.");
            try (var store = new AuctionStore("jdbc:sqlite:" + file);
                 var server = new AuctionServer(store, host, port)) {
                String adminPassword = System.getenv("AUCTION_ADMIN_PASSWORD");
                if (demo && adminPassword == null) adminPassword = "Phien08demo";
                if (adminPassword != null && store.account("admin") == null)
                    server.users.create("admin", adminPassword, User.Role.ADMIN);
                if (demo) seed(store, server);
                System.out.printf("PHIÊN 08 | %s:%d | %s%n", host, server.port(), file);
                server.serve();
            }
        }
    }
    private static void seed(AuctionStore store, AuctionServer server) throws Exception {
        if (store.account("nguoimua") == null) server.users.create("nguoimua", "Phien08demo", User.Role.BIDDER);
        if (store.account("nhaban") != null) return;
        var seller = server.users.create("nhaban", "Phien08demo", User.Role.SELLER);
        long now = System.currentTimeMillis();
        server.manager.publish(seller, null, new Listing("Máy ảnh Fujifilm X-T30", "Thân máy màu bạc, kèm pin và dây đeo. Người mua có thể kiểm tra máy khi nhận.", Item.Category.ELECTRONICS, 8_500_000, now, now + 7_200_000));
        server.manager.publish(seller, null, new Listing("Phố cổ sau mưa", "Tranh màu nước trên giấy cotton, 30 × 40 cm, có chữ ký tác giả.", Item.Category.ART, 650_000, now, now + 86_400_000));
        server.manager.publish(seller, null, new Listing("Xe đạp touring cổ điển", "Khung thép, bánh 700C và dàn truyền động 8 tốc độ.", Item.Category.VEHICLE, 2_400_000, now + 600_000, now + 86_400_000));
    }
}
