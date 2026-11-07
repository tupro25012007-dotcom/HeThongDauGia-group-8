package test;

import Server.repository.AuctionStore;
import Server.service.*;
import Shared.exception.AuctionException;
import Shared.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionManagerTest {
    @TempDir Path temp;
    final User seller = new Seller("seller"), alice = new Bidder("alice"), bob = new Bidder("bob"), admin = new Admin("admin");
    long now = 1_800_000_000_000L;
    AuctionStore store;
    AuctionManager manager;
    private void open() throws Exception {
        store = new AuctionStore("jdbc:sqlite:" + temp.resolve("test.db"));
        manager = new AuctionManager(store, new Clock() {
            @Override public Instant instant() { return Instant.ofEpochMilli(now); }
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
        }, () -> {});
    }
    @BeforeEach void start() throws Exception {
        open();
        for (var user : List.of(alice,bob)) manager.moveMoney(user,true,1000,UUID.randomUUID().toString());
    }
    @AfterEach void stop() throws Exception { store.close(); }
    Auction lot(long start, long end) throws Exception {
        return manager.publish(seller, null, new AuctionManager.Listing("Tranh", "Giấy dó", Item.Category.ART, 100, start, end));
    }
    @Test void listingPhotoAndPrivateWatchlistSurviveRestart() throws Exception {
        var form = new AuctionManager.Listing("Máy ảnh", "Thân máy", Item.Category.ELECTRONICS, 100, now, now + 60000);
        var file = Path.of(getClass().getResource("/gallery/electronics.png").toURI()).toFile();
        String encoded = ProductPhoto.prepare(file);
        var lot = manager.publish(seller, null, form, encoded);
        assertNotNull(lot.photoVersion());
        store.watch(alice.id(), lot.id(), true);
        store.close(); open();
        assertEquals(encoded, store.photo(lot.id()));
        assertEquals(Set.of(lot.id()), store.watched(alice.id()));
        assertTrue(store.watched(bob.id()).isEmpty());
        assertThrows(AuctionException.class, () -> manager.publish(seller, lot.id(), form, "not-an-image"));
        assertEquals(lot.photoVersion(), manager.publish(seller, lot.id(), form).photoVersion());
        assertNull(manager.publish(seller, lot.id(), form, "").photoVersion());
        assertNull(store.photo(lot.id()));
        manager.publish(seller, lot.id(), form, encoded);
        manager.delete(seller, lot.id());
        assertTrue(store.watched(alice.id()).isEmpty()); assertNull(store.photo(lot.id()));
    }
    @Test void clockAndSettlement() throws Exception {
        var a = lot(now + 1000, now + 20_000); String id = a.id();
        assertEquals(Auction.Status.OPEN, a.status());
        assertThrows(AuctionException.class, () -> manager.bid(alice, id, 200));
        now += 1000;
        assertEquals(Auction.Status.RUNNING, manager.bid(alice, id, 200).status());
        now = a.end();
        assertThrows(AuctionException.class, () -> manager.bid(bob, id, 300));
        assertEquals(Auction.Status.FINISHED, manager.list().getFirst().status());
        assertThrows(AuctionException.class, () -> manager.settle(bob, id, true));
        assertEquals(Auction.Status.PAID, manager.settle(alice, id, true).status());
        assertThrows(AuctionException.class, () -> manager.settle(admin, id, false));
    }
    @Test void simultaneousOffersHaveOneWinner() throws Exception {
        var id = lot(now, now + 60_000).id();
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Boolean>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                User bidder = new Bidder("b" + i);
                manager.moveMoney(bidder,true,110,UUID.randomUUID().toString());
                futures.add(pool.submit(() -> {
                    start.await();
                    try { manager.bid(bidder, id, 110); return true; }
                    catch (AuctionException e) { return false; }
                }));
            }
            start.countDown(); int wins = 0;
            for (var f : futures) if (f.get(5, TimeUnit.SECONDS)) wins++;
            assertEquals(1, wins);
        }
        assertEquals(1, manager.list().getFirst().bids().size());
    }
    @Test void privateCeilingsSurviveRestartAndTiesFavorEarlierRule() throws Exception {
        String id = lot(now, now + 120_000).id();
        assertEquals(105, manager.autoBid(alice, id, 300, 5).price());
        store.close(); open();
        var reply = manager.autoBid(bob, id, 300, 10);
        assertEquals(300, reply.price()); assertEquals("alice", reply.leader());
        assertEquals("bob", manager.bid(bob, id, 301).leader());
        assertThrows(AuctionException.class, () -> manager.autoBid(alice, id, 150, 3));
    }
    @Test void disablingPrivateRuleKeepsCommittedHistory() throws Exception {
        String id = lot(now, now + 60_000).id();
        manager.autoBid(alice, id, 500, 15);
        assertNull(manager.ownRule(bob, id));
        assertEquals(500, manager.ownRule(alice, id).maximum());
        manager.disableAuto(alice, id);
        assertNull(manager.ownRule(alice, id));
        assertEquals(List.of(115L, 120L), manager.bid(bob, id, 120).bids().stream().map(BidTransaction::amount).toList());
    }
    @Test void manualBidTriggersProxyAndExtendsOnce() throws Exception {
        var a = lot(now, now + 60_000); manager.autoBid(alice, a.id(), 500, 5);
        now = a.end() - 1000;
        var result = manager.bid(bob, a.id(), 155);
        assertEquals(160, result.price()); assertEquals(a.end() + 60_000, result.end());
        assertEquals(List.of(105L, 155L, 160L), result.bids().stream().map(BidTransaction::amount).toList());
        assertEquals(result.end(), manager.bid(bob, a.id(), 600).end());
    }
    @Test void walletReservesAcrossLotsAndPaysExactlyOnce() throws Exception {
        String first = lot(now,now+60_000).id(), second = lot(now,now+70_000).id();
        manager.autoBid(alice,first,800,20);
        assertEquals(800,manager.wallet(alice).held());
        assertThrows(AuctionException.class, () -> manager.bid(alice,second,201));
        assertThrows(AuctionException.class, () -> manager.moveMoney(alice,false,201,UUID.randomUUID().toString()));
        manager.disableAuto(alice,first);
        assertEquals(120,manager.wallet(alice).held());
        manager.bid(bob,first,125);
        assertEquals(0,manager.wallet(alice).held());
        manager.finish(admin,first);
        assertEquals(125,manager.wallet(bob).held());
        manager.settle(bob,first,true);
        assertThrows(AuctionException.class, () -> manager.settle(bob,first,true));
        store.close(); open();
        assertEquals(875,manager.wallet(bob).available());
        assertEquals(125,manager.wallet(seller).available());
        assertEquals(Auction.Status.PAID,store.get(first).auction().status());
        manager.autoBid(alice,second,900,5);
        manager.settle(admin,second,false);
        assertEquals(1000,manager.wallet(alice).available());
    }
    @Test void upgradingLegacyCommitmentsCreatesOpeningCreditOnlyOnce() throws Exception {
        String id = lot(now,now+60_000).id();
        manager.autoBid(alice,id,500,5);
        store.close();
        try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("test.db")); var q = db.createStatement()) {
            q.execute("DROP TABLE wallet_entry");
        }
        open();
        assertEquals(500,manager.wallet(alice).balance());
        assertEquals(0,manager.wallet(alice).available());
        store.close(); open();
        assertEquals(1,manager.wallet(alice).recent().size());
    }
    @Test void withdrawalIsIdempotentAndCannotOverdraw() throws Exception {
        String ref = UUID.randomUUID().toString();
        assertEquals(200,manager.moveMoney(alice,false,800,ref).balance());
        store.close(); open();
        assertEquals(200,manager.moveMoney(alice,false,800,ref).balance());
        assertThrows(AuctionException.class, () -> manager.moveMoney(alice,true,800,ref));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> withdraw = () -> {
                try { manager.moveMoney(alice,false,200,UUID.randomUUID().toString()); return true; }
                catch (AuctionException e) { return false; }
            };
            var one = pool.submit(withdraw); var two = pool.submit(withdraw);
            assertNotEquals(one.get(),two.get());
        }
        assertEquals(0,manager.wallet(alice).balance());
        assertThrows(AuctionException.class, () -> manager.moveMoney(alice,true,-10,ref));
    }
    @Test void ownerPermissionsAndAdminActions() throws Exception {
        var a = lot(now, now + 60_000);
        assertThrows(AuctionException.class, () -> manager.delete(new Seller("other"), a.id()));
        assertThrows(AuctionException.class, () -> manager.bid(seller, a.id(), 200));
        assertThrows(AuctionException.class, () -> manager.bid(alice, a.id(), 99));
        assertThrows(AuctionException.class, () -> manager.finish(seller, a.id()));
        manager.bid(alice, a.id(), 120);
        assertThrows(AuctionException.class, () -> manager.delete(seller, a.id()));
        assertEquals("alice", manager.finish(admin, a.id()).leader());
        assertEquals(Auction.Status.CANCELED, manager.settle(admin, a.id(), false).status());
    }
    @Test void sellerEditsAndDeletesUnusedLot() throws Exception {
        var a = lot(now, now + 60_000);
        var form = new AuctionManager.Listing("Máy ảnh", "Ống kính", Item.Category.ELECTRONICS, 999, now, now + 50_000);
        assertEquals(999, manager.publish(seller, a.id(), form).opening());
        manager.delete(seller, a.id()); assertTrue(manager.list().isEmpty());
    }
    @Test void signupLoginAndRoles() throws Exception {
        var users = new UserService(store);
        users.register(" Alice ", "correct123", User.Role.BIDDER);
        assertEquals(User.Role.BIDDER, users.login("ALICE", "correct123").role());
        assertNotEquals("correct123", store.account("alice").hash());
        assertThrows(AuctionException.class, () -> users.login("alice", "wrong123"));
        assertThrows(AuctionException.class, () -> users.register("alice", "correct123", User.Role.SELLER));
        assertThrows(AuctionException.class, () -> users.register("another", "correct123", User.Role.ADMIN));
    }
}
