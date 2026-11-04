package com.example.auction.common.model;

/** Observer: ai muốn biết khi có giá mới thì implement interface này. */
public interface BidListener {
  void onNewBid(String auctionId, BidTransaction bid);
}
