package test;

import Client.network.AuctionClient;
import Server.network.AuctionServer;
import Server.repository.AuctionStore;
import Shared.model.*;
import Shared.protocol.Wire;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static Shared.protocol.Wire.fields;

class NetworkTest {
    @Test void accountChangesArePrivateAndRevokeOldSessions() throws Exception {
        try (var store = new AuctionStore("jdbc:sqlite::memory:");
             var server = new AuctionServer(store,"127.0.0.1",0)) {
            Thread.startVirtualThread(() -> { try { server.serve(); } catch (Exception e) { throw new RuntimeException(e); } });
            server.users.create("admin","original123",User.Role.ADMIN);
            var revoked = new CountDownLatch(1);
            try (var owner = new AuctionClient("127.0.0.1",server.port(),()->{},()->{});
                 var other = new AuctionClient("127.0.0.1",server.port(),()->{},revoked::countDown);
                 var admin = new AuctionClient("127.0.0.1",server.port(),()->{},()->{})) {
                var credentials = fields("name","owner","password","oldpass123","role","BIDDER");
                owner.call("register",credentials); other.call("login",credentials);
                admin.call("login",fields("name","admin","password","original123"));
                owner.call("rename",fields("display","Minh Anh","target","admin"));
                assertEquals("Minh Anh",owner.call("profile",fields()).getAsJsonObject().get("displayName").getAsString());
                assertThrows(IllegalArgumentException.class, () -> owner.call("accounts",fields()));
                var target = fields("target","owner","locked",true);
                assertThrows(IllegalArgumentException.class, () -> owner.call("lockAccount",target));
                assertThrows(IllegalArgumentException.class, () -> owner.call("password",fields("current","wrongpass","next","newpass123")));
                owner.call("password",fields("current","oldpass123","next","newpass123"));
                assertTrue(revoked.await(2,TimeUnit.SECONDS));
                assertEquals(2,admin.call("accounts",fields()).getAsJsonArray().size());
                admin.call("lockAccount",target);
                try (var fresh = new AuctionClient("127.0.0.1",server.port(),()->{},()->{})) {
                    assertThrows(IllegalArgumentException.class, () -> fresh.call("login",fields("name","owner","password","newpass123")));
                    admin.call("lockAccount",fields("target","owner","locked",false));
                    assertThrows(IllegalArgumentException.class, () -> fresh.call("login",credentials));
                    fresh.call("login",fields("name","owner","password","newpass123"));
                    assertEquals(0,fresh.call("wallet",fields()).getAsJsonObject().get("balance").getAsLong());
                }
            }
        }
    }
    @Test void twoClientsSeeCommittedBidAndPerSocketPermissions() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            try (var store = new AuctionStore("jdbc:sqlite::memory:");
                 var server = new AuctionServer(store, "127.0.0.1", 0)) {
                Thread.startVirtualThread(() -> { try { server.serve(); } catch (Exception e) { throw new RuntimeException(e); } });
                var signals = new LinkedBlockingQueue<Boolean>();
                try (var seller = new AuctionClient("127.0.0.1", server.port(), () -> signals.add(true), () -> {});
                     var buyer = new AuctionClient("127.0.0.1", server.port(), () -> {}, () -> {})) {
                    assertThrows(IllegalArgumentException.class, () -> buyer.call("list", fields()));
                    seller.call("register", fields("name","seller","password","correct123","role","SELLER"));
                    buyer.call("register", fields("name","buyer","password","correct321","role","BIDDER"));
                    buyer.call("deposit", fields("amount",200,"requestId",java.util.UUID.randomUUID().toString()));
                    assertNotNull(signals.poll(2, TimeUnit.SECONDS));
                    long now = System.currentTimeMillis();
                    String id = seller.call("publish", fields("name","Tranh","description","Sơn dầu","category","ART",
                            "opening",100,"start",now,"end",now+120000)).getAsJsonObject().get("id").getAsString();
                    assertNotNull(signals.poll(2, TimeUnit.SECONDS));
                    buyer.call("bid", fields("id", id, "amount", 120, "name", "admin"));
                    assertNotNull(signals.poll(2, TimeUnit.SECONDS));
                    Auction a = Wire.JSON.fromJson(seller.call("list", fields()).getAsJsonArray().get(0), Auction.class);
                    assertEquals("buyer", a.leader()); assertEquals(120, a.price());
                    assertThrows(IllegalArgumentException.class, () -> buyer.call("cancel", fields("id",id,"role","ADMIN")));
                    assertThrows(IllegalArgumentException.class, () -> buyer.call("bid", fields("id",id,"amount",120.5)));
                }
            }
        });
    }
}
