package com.example.auction.common.model;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/** Aggregate root: state and bid history share one per-auction lock. */
public class Auction extends Entity {
  // Keep the identifier used by existing snapshots.
  private static final long serialVersionUID = -5799514962377660541L;
  public static final long MAX_AMOUNT = 1_000_000_000_000_000L;
  private Item item;
  private final String sellerId;
  private Instant startTime;
  private Instant endTime;
  private final ReentrantLock lock = new ReentrantLock();
  private transient List<BidListener> listeners = new CopyOnWriteArrayList<>();
  private final List<BidTransaction> history = new ArrayList<>();
  private long version;
  private AuctionStatus status = AuctionStatus.OPEN;
  private long currentPrice;
  private String leaderId;

  public Auction(Item item, String sellerId, Instant endTime) {
    this(item, sellerId, Instant.now(), endTime);
  }

  /** Creates an instance with the supplied validated auction details. */
  public Auction(Item item, String sellerId, Instant startTime, Instant endTime) {
    if (item == null
        || sellerId == null
        || sellerId.isBlank()
        || startTime == null
        || endTime == null
        || !endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("Thông tin/thời gian phiên không hợp lệ");
    }
    this.item = item;
    this.sellerId = sellerId;
    this.startTime = startTime;
    this.endTime = endTime;
    currentPrice = item.getStartingPrice();
  }

  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    listeners = new CopyOnWriteArrayList<>();
  }

  public void addListener(BidListener listener) {
    listeners.add(listener);
  }

  public void removeListener(BidListener listener) {
    listeners.remove(listener);
  }

  /** Validates the auction state transition before applying it under the lock. */
  public void changeStatus(AuctionStatus next) {
    lock.lock();
    try {
      if (next == null || !status.canTransitionTo(next)) {
        throw new AuctionException("Không thể chuyển " + status + " -> " + next);
      }
      if (next == AuctionStatus.PAID && leaderId == null) {
        throw new AuctionException("Phiên không có người thắng");
      }
      status = next;
      version++;
    } finally {
      lock.unlock();
    }
  }

  /** Starts a draft now while preserving its configured duration. */
  public void startNow(Instant now) {
    lock.lock();
    try {
      if (status != AuctionStatus.OPEN) {
        throw new AuctionException("Phiên không còn OPEN");
      }
      Duration duration = Duration.between(startTime, endTime);
      startTime = now;
      endTime = now.plus(duration);
      changeStatus(AuctionStatus.RUNNING);
    } finally {
      lock.unlock();
    }
  }

  /** Replaces draft details; active and completed auctions cannot be edited. */
  public void update(Item replacement, Instant start, Instant end) {
    lock.lock();
    try {
      if (status != AuctionStatus.OPEN) {
        throw new AuctionException("Chỉ sửa phiên OPEN");
      }
      if (replacement == null || start == null || end == null || !end.isAfter(start)) {
        throw new IllegalArgumentException("Thời gian không hợp lệ");
      }
      item = replacement;
      startTime = start;
      endTime = end;
      currentPrice = replacement.getStartingPrice();
      version++;
    } finally {
      lock.unlock();
    }
  }

  /** Accepts one bid and notifies observers after releasing the lock. */
  public BidTransaction placeBid(String bidderId, long amount, Instant now) {
    BidTransaction bid;
    lock.lock();
    try {
      requireRunning(bidderId, now);
      if (amount <= currentPrice || amount > MAX_AMOUNT) {
        throw new InvalidBidException("Giá phải lớn hơn " + currentPrice + " và ≤ " + MAX_AMOUNT);
      }
      bid = new BidTransaction(bidderId, amount, now);
      currentPrice = amount;
      leaderId = bidderId;
      history.add(bid);
      version++;
    } finally {
      lock.unlock();
    }
    notifyListeners(bid);
    return bid;
  }

  private void requireRunning(String bidderId, Instant now) {
    if (bidderId == null || bidderId.isBlank() || bidderId.equals(sellerId)) {
      throw new InvalidBidException("Người bán không được tự đấu giá");
    }
    if (status != AuctionStatus.RUNNING || now.isBefore(startTime) || !now.isBefore(endTime)) {
      throw new AuctionClosedException("Phiên chưa mở hoặc đã kết thúc");
    }
  }

  private void notifyListeners(BidTransaction bid) {
    for (BidListener listener : listeners) {
      try {
        listener.onNewBid(getId(), bid);
      } catch (RuntimeException ignored) {
        /* One observer must not reject a committed bid. */
      }
    }
  }

  /** Returns the current price. */
  public long getCurrentPrice() {
    lock.lock();
    try {
      return currentPrice;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the leader id. */
  public String getLeaderId() {
    lock.lock();
    try {
      return leaderId;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the status. */
  public AuctionStatus getStatus() {
    lock.lock();
    try {
      return status;
    } finally {
      lock.unlock();
    }
  }

  /** Returns an immutable copy of accepted bids in commit order. */
  public List<BidTransaction> getHistory() {
    lock.lock();
    try {
      return List.copyOf(history);
    } finally {
      lock.unlock();
    }
  }

  /** Returns the start time. */
  public Instant getStartTime() {
    lock.lock();
    try {
      return startTime;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the end time. */
  public Instant getEndTime() {
    lock.lock();
    try {
      return endTime;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the monotonically increasing snapshot version. */
  public long getVersion() {
    lock.lock();
    try {
      return version;
    } finally {
      lock.unlock();
    }
  }

  /** Returns the item. */
  public Item getItem() {
    lock.lock();
    try {
      return item;
    } finally {
      lock.unlock();
    }
  }

  public String getSellerId() {
    return sellerId;
  }

  @Override
  public String printInfo() {
    return "Phiên " + item.getName() + " | giá " + getCurrentPrice() + " | " + getStatus();
  }
}
