package com.example.auction.common.model;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.InvalidBidException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Phiên đấu giá (Auction) - Aggregate Root quản lý trạng thái, lịch sử giá và Observer.
 * Đảm bảo an toàn đa luồng (thread-safety) với ReentrantLock theo yêu cầu Tuần 6-9 BTL LTNC.
 */
public class Auction extends Entity {
  public static final long MAX_AMOUNT = 1_000_000_000_000_000L;

  private Item item;
  private final String sellerId;
  private Instant startTime;
  private Instant endTime;
  private boolean antiSniping;
  private final ReentrantLock lock = new ReentrantLock();

  private transient List<BidListener> listeners = new CopyOnWriteArrayList<>();
  private transient List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

  private final List<BidTransaction> history = new ArrayList<>();
  private final Map<String, AutoBid> autoBids = new HashMap<>();
  private long registrationSequence;
  private long version;
  private long bidVersion;
  private AuctionStatus status = AuctionStatus.OPEN;
  private long currentPrice;
  private String leaderId;
  private String winnerId;

  private static final ScheduledExecutorService autoCloseScheduler =
      Executors.newScheduledThreadPool(
          Math.max(2, Runtime.getRuntime().availableProcessors()),
          r -> {
            Thread t = new Thread(r, "auction-auto-close");
            t.setDaemon(true);
            return t;
          });

  private record AutoBid(String bidderId, long maxBid, long increment, long sequence)
      implements Serializable {}

  /** Khởi tạo phiên nhanh với endTime dạng Instant. */
  public Auction(Item item, String sellerId, Instant endTime) {
    this(item, sellerId, Instant.now(), endTime, false);
  }

  /** Khởi tạo phiên đầy đủ thông tin thời gian Instant. */
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
    this.currentPrice = item.getStartingPrice();
  }

  /** Khởi tạo phiên với LocalDateTime tương thích mã nguồn Tuần 6-8 của role auction. */
  public Auction(Item item, LocalDateTime startTime, LocalDateTime endTime) {
    this(
        item,
        "seller-default",
        startTime != null ? startTime.atZone(ZoneId.systemDefault()).toInstant() : null,
        endTime != null ? endTime.atZone(ZoneId.systemDefault()).toInstant() : null,
        false);
  }

  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    listeners = new CopyOnWriteArrayList<>();
    observers = new CopyOnWriteArrayList<>();
  }

  // ================= Observer Pattern (BidListener & AuctionObserver) =================

  public void addListener(BidListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public void removeListener(BidListener listener) {
    if (listener != null) {
      listeners.remove(listener);
    }
  }

  public void addObserver(AuctionObserver observer) {
    if (observer != null && !observers.contains(observer)) {
      observers.add(observer);
    }
  }

  public void removeObserver(AuctionObserver observer) {
    if (observer != null) {
      observers.remove(observer);
    }
  }

  // ================= Lifecycle & State Transitions =================

  /** Bắt đầu phiên chuyển sang RUNNING và lập lịch tự đóng khi chạm endTime. */
  public void start() {
    lock.lock();
    try {
      if (this.status != AuctionStatus.OPEN) {
        throw new IllegalStateException(
            "Chỉ có thể chuyển sang RUNNING từ OPEN. Trạng thái hiện tại: " + this.status);
      }
      this.status = AuctionStatus.RUNNING;
      this.version++;
      scheduleAutoCloseLocked();
    } finally {
      lock.unlock();
    }
  }

  /** Kết thúc phiên và chốt người thắng cuộc. */
  public void finish() {
    lock.lock();
    try {
      finishInternal();
    } finally {
      lock.unlock();
    }
  }

  private void finishInternal() {
    if (this.status == AuctionStatus.FINISHED
        || this.status == AuctionStatus.PAID
        || this.status == AuctionStatus.CANCELED) {
      return;
    }
    this.status = AuctionStatus.FINISHED;
    this.winnerId = this.leaderId;
    this.version++;
  }

  /** Đánh dấu phiên đã thanh toán (PAID). */
  public void markPaid() {
    lock.lock();
    try {
      if (this.status != AuctionStatus.FINISHED) {
        throw new IllegalStateException("Chỉ có thể chuyển sang PAID từ trạng thái FINISHED");
      }
      this.status = AuctionStatus.PAID;
      this.version++;
    } finally {
      lock.unlock();
    }
  }

  /** Hủy phiên đấu giá (CANCELED). */
  public void cancel() {
    lock.lock();
    try {
      if (this.status == AuctionStatus.PAID) {
        throw new IllegalStateException("Không thể hủy phiên đã thanh toán");
      }
      this.status = AuctionStatus.CANCELED;
      this.version++;
    } finally {
      lock.unlock();
    }
  }

  /** Chuyển đổi trạng thái với kiểm tra thứ tự hợp lệ. */
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
      if (next == AuctionStatus.FINISHED) {
        winnerId = leaderId;
      }
      version++;
    } finally {
      lock.unlock();
    }
  }

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

  public void update(Item replacement, Instant start, Instant end, boolean anti) {
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
      antiSniping = anti;
      currentPrice = replacement.getStartingPrice();
      version++;
    } finally {
      lock.unlock();
    }
  }

  // ================= Bidding (Thread-Safe với ReentrantLock) =================

  /**
   * Đặt giá trực tiếp dạng đối tượng Bidder và số tiền double (chuẩn tuần 6-8).
   */
  public void placeBid(Bidder bidder, double amount)
      throws AuctionClosedException, InvalidBidException {
    if (bidder == null) {
      throw new IllegalArgumentException("Bidder không được null");
    }
    placeBid(bidder.getUsername(), (long) amount, Instant.now());
  }

  /**
   * Đặt giá với bidderId, số tiền và thời điểm Instant.
   * Đồng thời giải quyết auto-bid và thông báo các observers.
   */
  public BidTransaction placeBid(String bidderId, long amount, Instant now) {
    List<BidTransaction> accepted = new ArrayList<>();
    double previousPrice;
    long currentBidVer;
    lock.lock();
    try {
      requireRunning(bidderId, now);
      if (amount <= currentPrice || amount > MAX_AMOUNT) {
        throw new InvalidBidException("Giá phải lớn hơn " + currentPrice + " và ≤ " + MAX_AMOUNT);
      }
      previousPrice = (double) currentPrice;
      currentBidVer = ++this.bidVersion;
      accept(bidderId, amount, now, accepted);
      resolveAutoBids(now, accepted);
    } finally {
      lock.unlock();
    }
    notifyAllObservers(accepted, previousPrice, currentBidVer);
    return accepted.getFirst();
  }

  /** Cấu hình auto-bid (đấu giá tự động với trần maxBid). */
  public void configureAutoBid(String bidderId, long maxBid, long increment, Instant now) {
    List<BidTransaction> accepted = new ArrayList<>();
    double previousPrice;
    long currentBidVer;
    lock.lock();
    try {
      requireRunning(bidderId, now);
      if (maxBid <= currentPrice
          || maxBid > MAX_AMOUNT
          || increment <= 0
          || increment > MAX_AMOUNT) {
        throw new InvalidBidException("Trần phải cao hơn giá hiện tại, bước giá phải > 0");
      }
      previousPrice = (double) currentPrice;
      currentBidVer = ++this.bidVersion;
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
    notifyAllObservers(accepted, previousPrice, currentBidVer);
  }

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
      throw new InvalidBidException("Người bán không được tự đấu giá hoặc mã người dùng trống");
    }
    if (status != AuctionStatus.RUNNING) {
      throw new AuctionClosedException("Phiên đấu giá hiện tại không ở trạng thái RUNNING. Trạng thái hiện tại: " + status);
    }
    if (now.isBefore(startTime)) {
      throw new AuctionClosedException("Phiên chưa mở lúc " + startTime);
    }
    if (!now.isBefore(endTime)) {
      finishInternal();
      throw new AuctionClosedException("Phiên đấu giá đã kết thúc lúc " + endTime);
    }
  }

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
      price = Math.min(winner.maxBid(), runner.maxBid() + winner.increment());
    }
    if (!winner.bidderId().equals(leaderId)) {
      price = Math.max(price, Math.min(winner.maxBid(), currentPrice + winner.increment()));
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

  private void scheduleAutoCloseLocked() {
    long delayMillis = Duration.between(Instant.now(), endTime).toMillis();
    if (delayMillis <= 0) {
      finishInternal();
    } else {
      autoCloseScheduler.schedule(this::finish, delayMillis, TimeUnit.MILLISECONDS);
    }
  }

  private void notifyAllObservers(List<BidTransaction> accepted, double previousPrice, long bidVer) {
    for (BidTransaction bid : accepted) {
      for (BidListener listener : listeners) {
        try {
          listener.onNewBid(getId(), bid);
        } catch (RuntimeException ignored) {
        }
      }
      for (AuctionObserver observer : observers) {
        try {
          BidEvent event =
              new BidEvent(
                  this,
                  new Bidder(bid.getBidderId(), ""),
                  (double) bid.getAmount(),
                  previousPrice,
                  bid.getBidTime(),
                  bidVer);
          observer.onBidPlaced(event);
        } catch (RuntimeException ignored) {
        }
      }
    }
  }

  // ================= Getters =================

  public long getCurrentPrice() {
    lock.lock();
    try {
      return currentPrice;
    } finally {
      lock.unlock();
    }
  }

  public double getCurrentHighestPrice() {
    return (double) getCurrentPrice();
  }

  public double getStartingPrice() {
    return (double) item.getStartingPrice();
  }

  public String getLeaderId() {
    lock.lock();
    try {
      return leaderId;
    } finally {
      lock.unlock();
    }
  }

  public Bidder getCurrentLeader() {
    lock.lock();
    try {
      return leaderId != null ? new Bidder(leaderId, "") : null;
    } finally {
      lock.unlock();
    }
  }

  public Bidder getWinner() {
    lock.lock();
    try {
      return winnerId != null ? new Bidder(winnerId, "") : null;
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
      return List.copyOf(history);
    } finally {
      lock.unlock();
    }
  }

  public List<BidTransaction> getBidHistory() {
    lock.lock();
    try {
      return Collections.unmodifiableList(new ArrayList<>(history));
    } finally {
      lock.unlock();
    }
  }

  public Instant getStartTime() {
    lock.lock();
    try {
      return startTime;
    } finally {
      lock.unlock();
    }
  }

  public Instant getEndTime() {
    lock.lock();
    try {
      return endTime;
    } finally {
      lock.unlock();
    }
  }

  public long getVersion() {
    lock.lock();
    try {
      return version;
    } finally {
      lock.unlock();
    }
  }

  public boolean isAntiSniping() {
    lock.lock();
    try {
      return antiSniping;
    } finally {
      lock.unlock();
    }
  }

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
