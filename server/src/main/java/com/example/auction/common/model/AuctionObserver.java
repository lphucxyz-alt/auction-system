package com.example.auction.common.model;

/**
 * Observer Pattern: Interface nhận thông báo khi có lượt đặt giá mới trong phiên.
 * Triển khai theo yêu cầu Tuần 7 của BTL LTNC.
 */
@FunctionalInterface
public interface AuctionObserver {
  void onBidPlaced(BidEvent event);
}
