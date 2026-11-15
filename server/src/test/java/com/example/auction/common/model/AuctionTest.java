package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ConcreteItem extends Item {
  public ConcreteItem(String name, String description, long startingPrice) {
    super(name, description, startingPrice);
  }

  @Override
  public String getCategory() {
    return "TEST";
  }
}

public class AuctionTest {
  private Item item;
  private Bidder bidder1;

  @BeforeEach
  void setUp() {
    item = new ConcreteItem("Laptop Dell", "Core i7 16GB", 1000L);
    bidder1 = new Bidder("user1", "pass1");
  }

  private Auction newRunningAuction() {
    Item artItem = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000L, "A", 2000);
    Auction a = new Auction(artItem, "seller", Instant.now().plusSeconds(60));
    a.changeStatus(AuctionStatus.RUNNING);
    return a;
  }

  // ================= Tests từ Repo phụ (Nguyễn Văn Mạnh - Tuần 6-8) =================

  @Test
  @DisplayName("Đặt giá hợp lệ thành công")
  void testPlaceValidBid() throws Exception {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    auction.start();
    auction.placeBid(bidder1, 1200.0);

    assertEquals(1200.0, auction.getCurrentHighestPrice());
    assertNotNull(auction.getCurrentLeader());
    assertEquals(bidder1.getUsername(), auction.getCurrentLeader().getUsername());
    assertEquals(1, auction.getBidHistory().size());
  }

  @Test
  @DisplayName("Đặt giá thấp hơn hoặc bằng giá hiện tại ném InvalidBidException")
  void testPlaceBidLowerOrEqualThanCurrent() {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    auction.start();

    assertThrows(InvalidBidException.class, () -> auction.placeBid(bidder1, 900.0));
    assertThrows(InvalidBidException.class, () -> auction.placeBid(bidder1, 1000.0));
  }

  @Test
  @DisplayName("Đặt giá khi phiên chưa RUNNING ném AuctionClosedException")
  void testPlaceBidWhenNotRunning() {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    assertThrows(AuctionClosedException.class, () -> auction.placeBid(bidder1, 1500.0));
  }

  @Test
  @DisplayName("Chuyển trạng thái hợp lệ OPEN -> RUNNING -> FINISHED -> PAID")
  void testValidStatusTransitionsToPaid() throws Exception {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    assertEquals(AuctionStatus.OPEN, auction.getStatus());

    auction.start();
    assertEquals(AuctionStatus.RUNNING, auction.getStatus());

    auction.placeBid(bidder1, 1200.0);
    auction.finish();
    assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    assertNotNull(auction.getWinner());
    assertEquals(bidder1.getUsername(), auction.getWinner().getUsername());

    auction.markPaid();
    assertEquals(AuctionStatus.PAID, auction.getStatus());
  }

  @Test
  @DisplayName("Chuyển trạng thái sai thứ tự và hủy phiên")
  void testInvalidStatusTransitionAndCancel() {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    assertThrows(IllegalStateException.class, auction::markPaid);

    auction.start();
    assertThrows(IllegalStateException.class, auction::start);

    auction.finish();
    auction.markPaid();
    assertThrows(IllegalStateException.class, auction::cancel);

    Auction cancelable = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    cancelable.cancel();
    assertEquals(AuctionStatus.CANCELED, cancelable.getStatus());
  }

  @Test
  @DisplayName("Tự động đóng phiên khi hết giờ và từ chối bid sau endTime")
  void testAutoCloseOnExpirationAndRejectLateBid() throws Exception {
    LocalDateTime start = LocalDateTime.now().minusSeconds(1);
    LocalDateTime end = LocalDateTime.now().plusNanos(150_000_000L);
    Auction auction = new Auction(item, start, end);
    auction.start();
    auction.placeBid(bidder1, 1100.0);

    Thread.sleep(300);

    assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    assertNotNull(auction.getWinner());
    assertEquals(bidder1.getUsername(), auction.getWinner().getUsername());
    assertThrows(AuctionClosedException.class, () -> auction.placeBid(bidder1, 1300.0));
  }

  @Test
  @DisplayName("Observer nhận đúng BidEvent kèm version tăng dần")
  void testObserverNotifiedWithNewBidEvent() throws Exception {
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    auction.start();

    AtomicInteger notifiedCount = new AtomicInteger(0);
    auction.addObserver(
        event -> {
          int c = notifiedCount.incrementAndGet();
          assertEquals(auction, event.getAuction());
          assertEquals(bidder1.getUsername(), event.getBidder().getUsername());
          assertEquals(c, event.getVersion());
        });

    auction.placeBid(bidder1, 1300.0);
    auction.placeBid(bidder1, 1400.0);
    assertEquals(2, notifiedCount.get());
  }

  @RepeatedTest(value = 100, name = "Kiểm thử đa luồng lần {currentRepetition}/100")
  @DisplayName("50 luồng đặt giá đồng thời không bị lost update, giá cuối luôn là 1500.0")
  void testConcurrentBidding100Runs() throws InterruptedException {
    int threadCount = 50;
    Auction auction = new Auction(item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    auction.start();

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successBids = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              Bidder b = new Bidder("user" + index, "pass");
              double bidAmount = 1000.0 + (index + 1) * 10.0;
              auction.placeBid(b, bidAmount);
              successBids.incrementAndGet();
            } catch (InvalidBidException | AuctionClosedException ignored) {
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(completed, "Các luồng phải hoàn thành trong vòng 5 giây");
    assertTrue(successBids.get() >= 1, "Phải có ít nhất 1 bid thành công");

    List<BidTransaction> history = auction.getBidHistory();
    long lastPrice = 1000L;
    for (BidTransaction tx : history) {
      assertTrue(tx.getAmount() > lastPrice, "Giá trong lịch sử phải tăng dần nghiêm ngặt");
      lastPrice = tx.getAmount();
    }

    assertEquals(1500.0, auction.getCurrentHighestPrice());
    assertNotNull(auction.getCurrentLeader());
    assertEquals("user49", auction.getCurrentLeader().getUsername());
  }

  // ================= Tests bổ sung từ Repo chính =================

  @Test
  void rejectsLowerBid() {
    Auction a = newRunningAuction();
    a.placeBid("b1", 2000L, Instant.now());
    assertThrows(InvalidBidException.class, () -> a.placeBid("b2", 2000L, Instant.now()));
  }

  @Test
  void rejectsBidWhenNotRunning() {
    Item artItem = ItemFactory.create(ItemType.ART, "Tranh", "x", 1000L, "A", 2000);
    Auction a = new Auction(artItem, "seller", Instant.now().plusSeconds(60));
    assertThrows(AuctionClosedException.class, () -> a.placeBid("b1", 5000L, Instant.now()));
  }

  @Test
  void notifiesListener() {
    Auction a = newRunningAuction();
    AtomicInteger calls = new AtomicInteger();
    a.addListener((id, bid) -> calls.incrementAndGet());
    a.placeBid("b1", 2000L, Instant.now());
    assertEquals(1, calls.get());
  }

  @Test
  void concurrentBidsKeepConsistent() throws Exception {
    Auction a = newRunningAuction();
    int threads = 100;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);

    for (int i = 1; i <= threads; i++) {
      final int n = i;
      pool.submit(
          () -> {
            ready.countDown();
            try {
              go.await();
              a.placeBid("bidder" + n, 1000L + n * 100L, Instant.now());
            } catch (InvalidBidException | InterruptedException ignored) {
            }
          });
    }

    ready.await();
    go.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

    List<BidTransaction> bids = a.getHistory();
    for (int i = 1; i < bids.size(); i++) {
      assertTrue(bids.get(i).getAmount() > bids.get(i - 1).getAmount());
    }
    assertEquals(a.getCurrentPrice(), bids.getLast().getAmount());
  }
}
