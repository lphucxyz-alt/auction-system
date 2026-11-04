package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdvancedAuctionTest {
  private final Instant now = Instant.parse("2026-11-03T12:00:00Z");

  private Auction auction(boolean anti) {
    Auction a =
        new Auction(
            new Art("Lot", "Description", 100, "Artist", 2026),
            "seller",
            now.minusSeconds(1),
            now.plusSeconds(5),
            anti);
    a.changeStatus(AuctionStatus.RUNNING);
    return a;
  }

  @Test
  void firstBidMustExceedStartingPrice() {
    Auction a = auction(false);
    assertThrows(InvalidBidException.class, () -> a.placeBid("buyer", 100, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("seller", 200, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("", 200, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("buyer", Auction.MAX_AMOUNT + 1, now));
    assertThrows(AuctionClosedException.class, () -> a.placeBid("buyer", 200, a.getEndTime()));
  }

  @Test
  void stateMachineAndNoWinnerPayment() {
    Auction a = auction(false);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.OPEN));
    a.changeStatus(AuctionStatus.FINISHED);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.PAID));
    a.changeStatus(AuctionStatus.CANCELED);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.RUNNING));
  }

  @Test
  void paymentAfterWinning() {
    Auction a = auction(false);
    a.placeBid("buyer", 101, now);
    a.changeStatus(AuctionStatus.FINISHED);
    a.changeStatus(AuctionStatus.PAID);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.CANCELED));
  }

  @Test
  void automaticCapsAndTiePriority() {
    Auction a = auction(false);
    a.configureAutoBid("early", 500, 10, now);
    assertEquals(110, a.getCurrentPrice());
    a.configureAutoBid("late", 500, 10, now);
    assertEquals("early", a.getLeaderId());
    assertEquals(500, a.getCurrentPrice());
    assertTrue(a.getHistory().stream().allMatch(b -> b.getAmount() <= 500));
  }

  @Test
  void proxyNeverExceedsCapAndManualBidCanWin() {
    Auction a = auction(false);
    a.configureAutoBid("auto", 300, 10, now);
    a.placeBid("manual", 200, now);
    assertEquals("auto", a.getLeaderId());
    assertEquals(210, a.getCurrentPrice());
    a.placeBid("manual", 301, now);
    assertEquals("manual", a.getLeaderId());
    assertEquals(301, a.getCurrentPrice());
    a.configureAutoBid("other", 1000, Auction.MAX_AMOUNT, now);
    assertEquals(1000, a.getCurrentPrice());
    assertThrows(InvalidBidException.class, () -> a.configureAutoBid("bad", 999, 1, now));
    assertThrows(InvalidBidException.class, () -> a.configureAutoBid("bad", 2000, 0, now));
  }

  @Test
  void removeAutoBidStopsResponses() {
    Auction a = auction(false);
    a.configureAutoBid("auto", 1000, 10, now);
    a.removeAutoBid("auto");
    a.placeBid("manual", 500, now);
    assertEquals("manual", a.getLeaderId());
    assertEquals(500, a.getCurrentPrice());
  }

  @Test
  void antiSnipingOnlyChangesLastSeconds() {
    Auction a = auction(true);
    a.placeBid("buyer", 101, now);
    assertEquals(now.plusSeconds(30), a.getEndTime());
    a.placeBid("buyer", 102, now.plusSeconds(1));
    assertEquals(now.plusSeconds(30), a.getEndTime());
    a.placeBid("buyer", 103, now.plusSeconds(25));
    assertEquals(now.plusSeconds(55), a.getEndTime());
    Auction disabled = auction(false);
    disabled.placeBid("buyer", 101, now);
    assertEquals(now.plusSeconds(5), disabled.getEndTime());
  }

  @Test
  void observerFailureCannotFailBidAndHistoryImmutable() {
    Auction a = auction(false);
    AtomicInteger seen = new AtomicInteger();
    a.addListener(
        (id, bid) -> {
          throw new IllegalStateException("slow observer");
        });
    BidListener listener = (id, bid) -> seen.incrementAndGet();
    a.addListener(listener);
    a.placeBid("buyer", 101, now);
    assertEquals(1, seen.get());
    a.removeListener(listener);
    a.placeBid("buyer", 102, now);
    assertEquals(1, seen.get());
    assertThrows(UnsupportedOperationException.class, () -> a.getHistory().clear());
  }

  @Test
  void invalidConstructionAndDraftUpdate() {
    assertThrows(IllegalArgumentException.class, () -> new Art("", "", 100, "A", 2000));
    assertThrows(IllegalArgumentException.class, () -> new Art("Art", "", 0, "A", 2000));
    assertThrows(IllegalArgumentException.class, () -> new Auction(null, "seller", now));
    Auction a =
        new Auction(
            new Vehicle("Vespa", "", 100, "Piaggio", 0), "seller", now, now.plusSeconds(10), true);
    a.update(
        new Electronics("Camera", "", 200, "Fuji", 12),
        now.plusSeconds(3),
        now.plusSeconds(23),
        false);
    assertEquals(200, a.getCurrentPrice());
    assertEquals("ELECTRONICS", a.getItem().getCategory());
    a.startNow(now.plusSeconds(1));
    assertEquals(now.plusSeconds(21), a.getEndTime());
    assertThrows(
        AuctionException.class, () -> a.update(a.getItem(), now, now.plusSeconds(3), false));
    assertThrows(AuctionException.class, () -> a.startNow(now));
  }

  @Test
  void autoBidAtMaximumAmountStillHasOneLeader() {
    Auction a = auction(false);
    a.configureAutoBid("first", Auction.MAX_AMOUNT, Auction.MAX_AMOUNT, now);
    assertEquals(Auction.MAX_AMOUNT, a.getCurrentPrice());
    assertEquals("first", a.getLeaderId());
    assertThrows(InvalidBidException.class, () -> a.placeBid("second", Auction.MAX_AMOUNT, now));
  }

  @Test
  void draftRejectsIncompleteUpdateWithoutLosingItem() {
    Item original = new Art("Tranh", "", 100, "A", 2026);
    Auction a = new Auction(original, "seller", now, now.plusSeconds(60), false);
    assertThrows(
        IllegalArgumentException.class, () -> a.update(null, now, now.plusSeconds(60), false));
    assertEquals(original, a.getItem());
    assertEquals(100, a.getCurrentPrice());
  }

  @Test
  void mixedBidsAndRuleChangesPreservePriceAndCapsForFixedSeeds() {
    for (int seed = 0; seed < 20; seed++) {
      Auction a = auction(false);
      Random random = new Random(seed);
      Map<String, Long> caps = new HashMap<>();
      for (int step = 0; step < 200; step++) {
        long before = a.getCurrentPrice();
        int historySize = a.getHistory().size();
        String bidder = "buyer" + random.nextInt(5);
        int operation = random.nextInt(4);
        long manual = before + 1 + random.nextInt(100);
        switch (operation) {
          case 0 -> a.placeBid(bidder, manual, now);
          case 1 -> {
            long cap = before + 1 + random.nextInt(5000);
            caps.put(bidder, cap);
            a.configureAutoBid(bidder, cap, 1 + random.nextInt(100), now);
          }
          case 2 -> {
            caps.remove(bidder);
            a.removeAutoBid(bidder);
          }
          default -> assertThrows(InvalidBidException.class, () -> a.placeBid(bidder, before, now));
        }
        var history = a.getHistory();
        long previous = before;
        for (int i = historySize; i < history.size(); i++) {
          BidTransaction bid = history.get(i);
          assertTrue(bid.getAmount() > previous, "seed=" + seed + " step=" + step);
          if (!(operation == 0 && i == historySize)) {
            assertTrue(caps.containsKey(bid.getBidderId()));
            assertTrue(bid.getAmount() <= caps.get(bid.getBidderId()));
          }
          previous = bid.getAmount();
        }
        assertEquals(previous, a.getCurrentPrice());
        if (!history.isEmpty()) {
          assertEquals(history.getLast().getBidderId(), a.getLeaderId());
        }
      }
    }
  }

  @Test
  void closingAndBidRaceHaveOneConsistentOutcome() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (int iteration = 0; iteration < 50; iteration++) {
        Auction a = auction(false);
        CountDownLatch go = new CountDownLatch(1);
        var bid =
            pool.submit(
                () -> {
                  go.await();
                  try {
                    a.placeBid("buyer", 200, now);
                    return true;
                  } catch (AuctionClosedException expected) {
                    return false;
                  }
                });
        var close =
            pool.submit(
                () -> {
                  go.await();
                  a.changeStatus(AuctionStatus.FINISHED);
                  return true;
                });
        go.countDown();
        boolean accepted = bid.get(3, TimeUnit.SECONDS);
        close.get(3, TimeUnit.SECONDS);
        assertEquals(AuctionStatus.FINISHED, a.getStatus());
        assertEquals(accepted ? 1 : 0, a.getHistory().size());
        assertEquals(accepted ? 200 : 100, a.getCurrentPrice());
        assertEquals(accepted ? "buyer" : null, a.getLeaderId());
      }
    }
  }
}
