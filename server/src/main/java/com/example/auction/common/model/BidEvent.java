package com.example.auction.common.model;

import java.time.LocalDateTime;

/**
 * Event chứa thông tin chi tiết của một lượt đặt giá mới để gửi đến các AuctionObserver.
 * Triển khai theo yêu cầu Tuần 7 của BTL LTNC.
 */
public final class BidEvent {
  private final Auction auction;
  private final Bidder bidder;
  private final double amount;
  private final double previousPrice;
  private final LocalDateTime timestamp;
  private final long version;

  public BidEvent(
      Auction auction,
      Bidder bidder,
      double amount,
      double previousPrice,
      LocalDateTime timestamp) {
    this(auction, bidder, amount, previousPrice, timestamp, 0L);
  }

  public BidEvent(
      Auction auction,
      Bidder bidder,
      double amount,
      double previousPrice,
      LocalDateTime timestamp,
      long version) {
    if (auction == null || bidder == null || timestamp == null) {
      throw new IllegalArgumentException("Auction, bidder và timestamp không được null");
    }
    if (!Double.isFinite(amount) || !Double.isFinite(previousPrice)) {
      throw new IllegalArgumentException("Giá đặt phải là số thực hợp lệ");
    }
    this.auction = auction;
    this.bidder = bidder;
    this.amount = amount;
    this.previousPrice = previousPrice;
    this.timestamp = timestamp;
    this.version = version;
  }

  public Auction getAuction() {
    return auction;
  }

  public Bidder getBidder() {
    return bidder;
  }

  public double getAmount() {
    return amount;
  }

  public double getPreviousPrice() {
    return previousPrice;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public long getVersion() {
    return version;
  }
}
