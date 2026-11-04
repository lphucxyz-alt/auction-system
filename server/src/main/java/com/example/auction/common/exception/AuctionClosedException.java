package com.example.auction.common.exception;

/** Signals a bid outside the running auction time window. */
public class AuctionClosedException extends AuctionException {
  public AuctionClosedException(String message) {
    super(message);
  }
}
