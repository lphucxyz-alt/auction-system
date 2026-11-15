package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuctionObserverTest {
  private Auction createRunningAuction() {
    Auction auction =
        new Auction(
            new Electronics("Laptop", "Test laptop", 100L, "Dell", 12),
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10));
    auction.start();
    return auction;
  }

  private Bidder createBidder(String u) {
    return new Bidder(u, "password");
  }

  @Test
  void shouldNotifyObserverAfterSuccessfulBid() throws Exception {
    Auction auction = createRunningAuction();
    List<BidEvent> events = new ArrayList<>();
    auction.addObserver(events::add);
    Bidder bidder = createBidder("alice");
    auction.placeBid(bidder, 150.0);
    assertEquals(1, events.size());
    assertSame(auction, events.get(0).getAuction());
    assertEquals(bidder.getUsername(), events.get(0).getBidder().getUsername());
    assertEquals(150.0, events.get(0).getAmount());
    assertEquals(100.0, events.get(0).getPreviousPrice());
    assertNotNull(events.get(0).getTimestamp());
  }

  @Test
  void shouldNotNotifyObserverWhenBidIsInvalid() {
    Auction auction = createRunningAuction();
    AtomicInteger count = new AtomicInteger();
    auction.addObserver(e -> count.incrementAndGet());
    assertThrows(InvalidBidException.class, () -> auction.placeBid(createBidder("alice"), 90.0));
    assertEquals(0, count.get());
    assertEquals(100.0, auction.getCurrentHighestPrice());
  }

  @Test
  void shouldStopNotifyingAfterObserverIsRemoved() throws Exception {
    Auction auction = createRunningAuction();
    AtomicInteger count = new AtomicInteger();
    AuctionObserver obs = e -> count.incrementAndGet();
    auction.addObserver(obs);
    auction.removeObserver(obs);
    auction.placeBid(createBidder("alice"), 150.0);
    assertEquals(0, count.get());
  }

  @Test
  void shouldNotifyEveryRegisteredObserver() throws Exception {
    Auction auction = createRunningAuction();
    AtomicInteger c1 = new AtomicInteger();
    AtomicInteger c2 = new AtomicInteger();
    auction.addObserver(e -> c1.incrementAndGet());
    auction.addObserver(e -> c2.incrementAndGet());
    auction.placeBid(createBidder("alice"), 150.0);
    assertEquals(1, c1.get());
    assertEquals(1, c2.get());
  }

  @Test
  void concurrentBidsShouldNotLoseTheHighestPrice() throws InterruptedException {
    Auction auction = createRunningAuction();
    int n = 20;
    CountDownLatch ready = new CountDownLatch(n);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger ok = new AtomicInteger();
    AtomicInteger notif = new AtomicInteger();
    auction.addObserver(e -> notif.incrementAndGet());
    List<Thread> threads = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      final int amt = 100 + i;
      Thread t =
          new Thread(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  auction.placeBid(createBidder("b" + amt), amt);
                  ok.incrementAndGet();
                } catch (InvalidBidException | AuctionClosedException ignored) {
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      threads.add(t);
      t.start();
    }
    ready.await();
    start.countDown();
    for (Thread t : threads) {
      t.join();
    }
    assertEquals(120.0, auction.getCurrentHighestPrice());
    assertTrue(ok.get() >= 1);
    assertEquals(ok.get(), notif.get());
  }

  @Test
  void shouldNotStopOtherObserversWhenOneObserverFails() {
    Auction auction = createRunningAuction();
    AtomicInteger count = new AtomicInteger();
    auction.addObserver(
        e -> {
          throw new IllegalStateException("fail");
        });
    auction.addObserver(e -> count.incrementAndGet());
    assertDoesNotThrow(() -> auction.placeBid(createBidder("alice"), 150.0));
    assertEquals(1, count.get());
    assertEquals(150.0, auction.getCurrentHighestPrice());
  }
}
