package com.example.auction.common.model;

import java.time.Instant;

/** Một lượt đặt giá đã được chấp nhận. Bất biến (không sửa được sau khi tạo). */
public final class BidTransaction implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
  private final String bidderId;
  private final long amount;
  private final Instant time;

  /** Creates an instance with the supplied identity and configuration. */
  public BidTransaction(String bidderId, long amount, Instant time) {
    this.bidderId = bidderId;
    this.amount = amount;
    this.time = time;
  }

  public String getBidderId() {
    return bidderId;
  }

  public long getAmount() {
    return amount;
  }

  public Instant getTime() {
    return time;
  }
}
