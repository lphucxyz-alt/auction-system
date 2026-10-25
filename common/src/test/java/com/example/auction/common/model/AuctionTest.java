package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.*;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class AuctionTest {
  private Auction newRunningAuction() {
    Item item = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000, "A", 2000);
    Auction a = new Auction(item, "seller", Instant.now().plusSeconds(60));
    a.changeStatus(AuctionStatus.RUNNING);
    return a;
  }

  @Test
  void rejectsLowerBid() {
    Auction a = newRunningAuction();
    a.placeBid("b1", 2000, Instant.now());
    assertThrows(InvalidBidException.class, () -> a.placeBid("b2", 2000, Instant.now()));
  }

  @Test
  void rejectsBidWhenNotRunning() {
    Item item = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000, "A", 2000);
    Auction a = new Auction(item, "seller", Instant.now().plusSeconds(60));
    assertThrows(AuctionClosedException.class, () -> a.placeBid("b1", 5000, Instant.now()));
  }

  @Test
  void notifiesListener() {
    Auction a = newRunningAuction();
    AtomicInteger calls = new AtomicInteger();
    a.addListener((id, bid) -> calls.incrementAndGet());
    a.placeBid("b1", 2000, Instant.now());
    assertEquals(1, calls.get());
  }

  @RepeatedTest(100)
  void concurrentBidsKeepConsistent() throws Exception {
    Auction a = newRunningAuction();
    int threads = 100;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);

    for (int i = 1; i <= threads; i++) {
      final int n = i;
      pool.submit(() -> {
        ready.countDown();
        try {
          go.await();
          a.placeBid("bidder" + n, 1000L + n * 100L, Instant.now());
        } catch (InvalidBidException | InterruptedException ignored) {
          // bị thua trong cuộc đua là bình thường
        }
      });
    }
    ready.await();
    go.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    assertEquals(1000L + threads * 100L, a.getCurrentPrice());
    assertEquals("bidder" + threads, a.getLeaderId());

    List<BidTransaction> h = a.getHistory();
    for (int i = 1; i < h.size(); i++) {
      assertTrue(h.get(i).getAmount() > h.get(i - 1).getAmount(), "Giá không được lùi");
    }
  }
}
