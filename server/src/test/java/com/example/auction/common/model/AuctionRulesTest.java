package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuctionRulesTest {
  private final Instant now = Instant.parse("2026-11-03T12:00:00Z");

  private Auction auction() {
    Auction a =
        new Auction(
            new Art("Lot", "Description", 100, "Artist", 2026),
            "seller",
            now.minusSeconds(1),
            now.plusSeconds(5));
    a.changeStatus(AuctionStatus.RUNNING);
    return a;
  }

  @Test
  void firstBidMustExceedStartingPrice() {
    Auction a = auction();
    assertThrows(InvalidBidException.class, () -> a.placeBid("buyer", 100, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("seller", 200, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("", 200, now));
    assertThrows(InvalidBidException.class, () -> a.placeBid("buyer", Auction.MAX_AMOUNT + 1, now));
    assertThrows(AuctionClosedException.class, () -> a.placeBid("buyer", 200, a.getEndTime()));
  }

  @Test
  void stateMachineAndNoWinnerPayment() {
    Auction a = auction();
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.OPEN));
    a.changeStatus(AuctionStatus.FINISHED);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.PAID));
    a.changeStatus(AuctionStatus.CANCELED);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.RUNNING));
  }

  @Test
  void paymentAfterWinning() {
    Auction a = auction();
    a.placeBid("buyer", 101, now);
    a.changeStatus(AuctionStatus.FINISHED);
    a.changeStatus(AuctionStatus.PAID);
    assertThrows(AuctionException.class, () -> a.changeStatus(AuctionStatus.CANCELED));
  }

  @Test
  void lastSecondBidPreservesDeadline() {
    Auction a = auction();
    Instant end = a.getEndTime();
    a.placeBid("buyer", 101, end.minusMillis(1));
    assertEquals(end, a.getEndTime());
    assertThrows(AuctionClosedException.class, () -> a.placeBid("buyer", 102, end));
  }

  @Test
  void observerFailureCannotFailBidAndHistoryImmutable() {
    Auction a = auction();
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
            new Vehicle("Vespa", "", 100, "Piaggio", 0), "seller", now, now.plusSeconds(10));
    a.update(
        new Electronics("Camera", "", 200, "Fuji", 12),
        now.plusSeconds(3),
        now.plusSeconds(23));
    assertEquals(200, a.getCurrentPrice());
    assertEquals("ELECTRONICS", a.getItem().getCategory());
    a.startNow(now.plusSeconds(1));
    assertEquals(now.plusSeconds(21), a.getEndTime());
    assertThrows(
        AuctionException.class, () -> a.update(a.getItem(), now, now.plusSeconds(3)));
    assertThrows(AuctionException.class, () -> a.startNow(now));
  }

  @Test
  void bidAtMaximumAmountStillHasOneLeader() {
    Auction a = auction();
    a.placeBid("first", Auction.MAX_AMOUNT, now);
    assertEquals(Auction.MAX_AMOUNT, a.getCurrentPrice());
    assertEquals("first", a.getLeaderId());
    assertThrows(InvalidBidException.class, () -> a.placeBid("second", Auction.MAX_AMOUNT, now));
  }

  @Test
  void draftRejectsIncompleteUpdateWithoutLosingItem() {
    Item original = new Art("Tranh", "", 100, "A", 2026);
    Auction a = new Auction(original, "seller", now, now.plusSeconds(60));
    assertThrows(
        IllegalArgumentException.class, () -> a.update(null, now, now.plusSeconds(60)));
    assertEquals(original, a.getItem());
    assertEquals(100, a.getCurrentPrice());
  }

  @Test
  void closingAndBidRaceHaveOneConsistentOutcome() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (int iteration = 0; iteration < 50; iteration++) {
        Auction a = auction();
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
