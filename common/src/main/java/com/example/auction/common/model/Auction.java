package com.example.auction.common.model;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class Auction extends Entity {
  private final Item item;
  private final String sellerId;
  private final Instant endTime;
  private final ReentrantLock lock = new ReentrantLock(); // khóa riêng của phiên này
  private final List<BidListener> listeners = new CopyOnWriteArrayList<>();
  private final List<BidTransaction> history = new ArrayList<>();

  // Mọi trường dưới đây chỉ được đọc/ghi khi đang giữ lock.
  private AuctionStatus status = AuctionStatus.OPEN;
  private long currentPrice;
  private String leaderId;

  public Auction(Item item, String sellerId, Instant endTime) {
    this.item = item;
    this.sellerId = sellerId;
    this.endTime = endTime;
    this.currentPrice = item.getStartingPrice();
  }

  public void addListener(BidListener listener) {
    listeners.add(listener);
  }

  public void removeListener(BidListener listener) {
    listeners.remove(listener);
  }

  public void changeStatus(AuctionStatus next) {
    lock.lock();
    try {
      if (!status.canTransitionTo(next)) {
        throw new AuctionException("Không thể chuyển " + status + " -> " + next);
      }
      status = next;
    } finally {
      lock.unlock();
    }
  }

  public BidTransaction placeBid(String bidderId, long amount, Instant now) {
    BidTransaction bid;
    lock.lock();
    try {
      if (status != AuctionStatus.RUNNING || !now.isBefore(endTime)) {
        throw new AuctionClosedException("Phiên không nhận giá (trạng thái " + status + ")");
      }
      boolean first = leaderId == null;
      if (first ? amount < currentPrice : amount <= currentPrice) {
        throw new InvalidBidException("Giá phải cao hơn " + currentPrice);
      }
      currentPrice = amount;
      leaderId = bidderId;
      bid = new BidTransaction(bidderId, amount, now);
      history.add(bid);
    } finally {
      lock.unlock();
    }
    // Ngoài khóa: listener chậm không chặn người khác đặt giá.
    for (BidListener l : listeners) {
      l.onNewBid(getId(), bid);
    }
    return bid;
  }

  public long getCurrentPrice() {
    lock.lock();
    try {
      return currentPrice;
    } finally {
      lock.unlock();
    }
  }

  public String getLeaderId() {
    lock.lock();
    try {
      return leaderId;
    } finally {
      lock.unlock();
    }
  }

  public AuctionStatus getStatus() {
    lock.lock();
    try {
      return status;
    } finally {
      lock.unlock();
    }
  }

  public List<BidTransaction> getHistory() {
    lock.lock();
    try {
      return List.copyOf(history); // trả bản sao, không lộ list gốc
    } finally {
      lock.unlock();
    }
  }

  public Item getItem() {
    return item;
  }

  public String getSellerId() {
    return sellerId;
  }

  @Override
  public String printInfo() {
    return "Phiên " + item.getName() + " | giá " + getCurrentPrice() + " | " + getStatus();
  }
}
