package com.example.auction.common.model;

import java.time.Instant;

/** Một lượt đặt giá đã được chấp nhận. Bất biến (không sửa được sau khi tạo). */
public final class BidTransaction {
  private final String bidderId;
  private final long amount;
  private final Instant time;

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
