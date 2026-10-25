package com.example.auction.server;

import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.model.Auction;
import com.example.auction.common.model.AuctionStatus;
import com.example.auction.common.model.BidTransaction;
import com.example.auction.common.model.Item;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionService {
  private final Map<String, Auction> auctions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

  public Auction create(Item item, String sellerId, Duration length) {
    Auction auction = new Auction(item, sellerId, Instant.now().plus(length));
    auctions.put(auction.getId(), auction);
    return auction;
  }

  /** Mở phiên và hẹn giờ tự đóng. Thời gian lấy theo đồng hồ của Server. */
  public void start(Auction auction) {
    auction.changeStatus(AuctionStatus.RUNNING);
    long delayMs = Math.max(0, Duration.between(Instant.now(), auction.getEndTime()).toMillis());
    scheduler.schedule(() -> close(auction), delayMs, TimeUnit.MILLISECONDS);
  }

  private void close(Auction auction) {
    try {
      auction.changeStatus(AuctionStatus.FINISHED);
    } catch (AuctionException e) {
      // phiên đã bị hủy trước đó: không có gì để đóng
    }
  }

  public BidTransaction placeBid(String auctionId, String bidderId, long amount) {
    Auction auction = auctions.get(auctionId);
    if (auction == null) {
      throw new AuctionException("Không tìm thấy phiên " + auctionId);
    }
    return auction.placeBid(bidderId, amount, Instant.now());
  }

  public void shutdown() {
    scheduler.shutdownNow();
  }
}
