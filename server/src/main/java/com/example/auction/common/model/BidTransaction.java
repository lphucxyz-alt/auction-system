package com.example.auction.common.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Một lượt đặt giá đã được chấp nhận trong phiên đấu giá.
 * Bất biến (immutable) và hỗ trợ Serialization theo yêu cầu Tuần 6 & 9.
 */
public final class BidTransaction implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String bidderId;
  private final long amount;
  private final Instant time;

  /** Khởi tạo giao dịch đặt giá với chuỗi bidderId và Instant time. */
  public BidTransaction(String bidderId, long amount, Instant time) {
    this.bidderId = bidderId;
    this.amount = amount;
    this.time = time;
  }

  /** Khởi tạo giao dịch đặt giá tương thích đối tượng Bidder và LocalDateTime. */
  public BidTransaction(Bidder bidder, double amount, LocalDateTime bidTime) {
    this.bidderId = bidder != null ? bidder.getUsername() : "";
    this.amount = (long) amount;
    this.time = bidTime != null
        ? bidTime.atZone(ZoneId.systemDefault()).toInstant()
        : Instant.now();
  }

  public String getBidderId() {
    return bidderId;
  }

  public Bidder getBidder() {
    return new Bidder(bidderId, "");
  }

  public long getAmount() {
    return amount;
  }

  public double getPrice() {
    return (double) amount;
  }

  public Instant getTime() {
    return time;
  }

  public LocalDateTime getBidTime() {
    return time.atZone(ZoneId.systemDefault()).toLocalDateTime();
  }
}
