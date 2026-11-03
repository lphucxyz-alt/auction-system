package com.example.auction.common.model;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/** Aggregate root: state, bid history and proxy bidding share one per-auction lock. */
public class Auction extends Entity {
  public static final long MAX_AMOUNT = 1_000_000_000_000_000L;
  private Item item;
  private final String sellerId;
  private Instant startTime;
  private Instant endTime;
  private boolean antiSniping;
  private final ReentrantLock lock = new ReentrantLock();
  private transient List<BidListener> listeners = new CopyOnWriteArrayList<>();
  private final List<BidTransaction> history = new ArrayList<>();
  private final Map<String, AutoBid> autoBids = new HashMap<>();
  private long registrationSequence;
  private long version;
  private AuctionStatus status = AuctionStatus.OPEN;
  private long currentPrice;
  private String leaderId;

  private record AutoBid(String bidderId, long maxBid, long increment, long sequence)
      implements Serializable {}

  public Auction(Item item, String sellerId, Instant endTime) {
    this(item, sellerId, Instant.now(), endTime, false);
  }

  /** Creates an instance with the supplied validated auction details. */
  public Auction(
      Item item, String sellerId, Instant startTime, Instant endTime, boolean antiSniping) {
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
    this.antiSniping = antiSniping;
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
  public void update(Item replacement, Instant start, Instant end, boolean anti) {
    lock.lock();
    try {
      if (status != AuctionStatus.OPEN) {
        throw new AuctionException("Chỉ sửa phiên OPEN");
      }
      if (!end.isAfter(start)) {
        throw new IllegalArgumentException("Thời gian không hợp lệ");
      }
      item = replacement;
      startTime = start;
      endTime = end;
      antiSniping = anti;
      currentPrice = replacement.getStartingPrice();
      version++;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Accepts a strictly increasing bid, resolves auto-bids and notifies observers after unlocking.
   */
  public BidTransaction placeBid(String bidderId, long amount, Instant now) {
    List<BidTransaction> accepted = new ArrayList<>();
    lock.lock();
    try {
      requireRunning(bidderId, now);
      if (amount <= currentPrice || amount > MAX_AMOUNT) {
        throw new InvalidBidException("Giá phải lớn hơn " + currentPrice + " và ≤ " + MAX_AMOUNT);
      }
      accept(bidderId, amount, now, accepted);
      resolveAutoBids(now, accepted);
    } finally {
      lock.unlock();
    }
    notifyListeners(accepted);
    return accepted.getFirst();
  }

  /** Registers a private maximum and increment, resolving competing caps deterministically. */
  public void configureAutoBid(String bidderId, long maxBid, long increment, Instant now) {
    List<BidTransaction> accepted = new ArrayList<>();
    lock.lock();
    try {
      requireRunning(bidderId, now);
      if (maxBid <= currentPrice
          || maxBid > MAX_AMOUNT
          || increment <= 0
          || increment > MAX_AMOUNT) {
        throw new InvalidBidException("Trần phải cao hơn giá hiện tại, bước giá phải > 0");
      }
      AutoBid previous = autoBids.get(bidderId);
      autoBids.put(
          bidderId,
          new AutoBid(
              bidderId,
              maxBid,
              increment,
              previous == null ? registrationSequence++ : previous.sequence()));
      version++;
      resolveAutoBids(now, accepted);
    } finally {
      lock.unlock();
    }
    notifyListeners(accepted);
  }

  /** Removes only this bidder's automatic bidding rule. */
  public void removeAutoBid(String bidderId) {
    lock.lock();
    try {
      autoBids.remove(bidderId);
      version++;
    } finally {
      lock.unlock();
    }
  }

  private void requireRunning(String bidderId, Instant now) {
    if (bidderId == null || bidderId.isBlank() || bidderId.equals(sellerId)) {
      throw new InvalidBidException("Người bán không được tự đấu giá");
    }
    if (status != AuctionStatus.RUNNING || now.isBefore(startTime) || !now.isBefore(endTime)) {
      throw new AuctionClosedException("Phiên chưa mở hoặc đã kết thúc");
    }
  }

  /** Resolve caps in O(n log n), without generating millions of synthetic one-step bids. */
  private void resolveAutoBids(Instant now, List<BidTransaction> accepted) {
    PriorityQueue<AutoBid> queue =
        new PriorityQueue<>(
            Comparator.comparingLong(AutoBid::maxBid)
                .reversed()
                .thenComparingLong(AutoBid::sequence));
    for (AutoBid bid : autoBids.values()) {
      if (bid.maxBid() >= currentPrice) {
        queue.add(bid);
      }
    }
    if (leaderId != null
        && (!autoBids.containsKey(leaderId) || autoBids.get(leaderId).maxBid() < currentPrice)) {
      queue.add(new AutoBid(leaderId, currentPrice, 1, Long.MIN_VALUE));
    }
    AutoBid winner = queue.poll();
    if (winner == null) {
      return;
    }
    AutoBid runner = queue.poll();
    long price = currentPrice;
    if (runner != null) {
      price =
          Math.min(
              winner.maxBid(),
              runner.maxBid() + Math.min(winner.increment(), MAX_AMOUNT - runner.maxBid()));
    }
    if (!winner.bidderId().equals(leaderId)) {
      price =
          Math.max(
              price,
              Math.min(
                  winner.maxBid(),
                  currentPrice + Math.min(winner.increment(), MAX_AMOUNT - currentPrice)));
    }
    if (price > currentPrice && price <= winner.maxBid()) {
      accept(winner.bidderId(), price, now, accepted);
    }
  }

  private void accept(String bidderId, long amount, Instant now, List<BidTransaction> accepted) {
    currentPrice = amount;
    leaderId = bidderId;
    BidTransaction bid = new BidTransaction(bidderId, amount, now);
    history.add(bid);
    accepted.add(bid);
    if (antiSniping && Duration.between(now, endTime).compareTo(Duration.ofSeconds(10)) <= 0) {
      endTime = now.plusSeconds(30);
    }
    version++;
  }

  private void notifyListeners(List<BidTransaction> accepted) {
    for (BidTransaction bid : accepted) {
      for (BidListener listener : listeners) {
        try {
          listener.onNewBid(getId(), bid);
        } catch (RuntimeException ignored) {
          /* One observer must not reject a committed bid. */
        }
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

  /** Returns whether last-second bids extend the auction deadline. */
  public boolean isAntiSniping() {
    lock.lock();
    try {
      return antiSniping;
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
