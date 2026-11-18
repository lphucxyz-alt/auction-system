package com.example.auction.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.AuthenticationException;
import com.example.auction.common.model.Auction;
import com.example.auction.common.model.AuctionStatus;
import com.example.auction.common.model.Item;
import com.example.auction.common.model.ItemFactory;
import com.example.auction.common.model.ItemType;
import com.example.auction.common.model.User;
import com.example.auction.server.dao.FileStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceTest {
  @Test
  void registerAndLogin() {
    UserService s = new UserService();
    s.register("an", "secret1", "BIDDER");
    assertEquals("BIDDER", s.login("an", "secret1").getRole());
    assertThrows(AuthenticationException.class, () -> s.login("an", "wrong"));
    assertThrows(AuthenticationException.class, () -> s.login("nobody", "secret1"));
    assertThrows(AuctionException.class, () -> s.register("an", "secret1", "BIDDER"));
  }

  @Test
  void storesHashNotPlainPassword() {
    User u = new UserService().register("binh", "secret1", "SELLER");
    assertFalse(u.getPasswordHash().contains("secret1"));
  }

  @Test
  void overdueDraftFinishesWithoutAnnouncingItAsOpenForBids(@TempDir Path data)
      throws Exception {
    Instant now = Instant.now();
    Item item = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000, "A", 2000);
    Auction draft = new Auction(item, "seller", now.minusSeconds(30), now.minusSeconds(10));
    try (FileStore store = new FileStore(data)) {
      store.save(new FileStore.State(List.of(), List.of(draft)));
      AuctionApplication app = new AuctionApplication(store);
      List<AuctionStatus> publishedStatuses = new ArrayList<>();
      app.addObserver((type, lot) -> publishedStatuses.add(lot.getStatus()));
      app.tick();
      assertEquals(List.of(AuctionStatus.FINISHED), publishedStatuses);
      assertEquals(AuctionStatus.FINISHED, store.load().auctions().getFirst().getStatus());
    }
  }

  @Test
  void autoClosesAndPicksWinner() throws Exception {
    AuctionService svc = new AuctionService();
    Item item = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000, "A", 2000);
    Auction a = svc.create(item, "seller", Duration.ofMillis(300));
    svc.start(a);
    svc.placeBid(a.getId(), "b1", 1500);
    svc.placeBid(a.getId(), "b2", 2000);

    Thread.sleep(600);

    assertEquals(AuctionStatus.FINISHED, a.getStatus());
    assertEquals("b2", a.getLeaderId());
    assertThrows(AuctionClosedException.class, () -> svc.placeBid(a.getId(), "b3", 9999));
    svc.shutdown();
  }
}
