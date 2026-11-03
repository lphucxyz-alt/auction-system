package com.example.auction.common.exception;

/** Signals a bid that violates the auction pricing or ownership rules. */
public class InvalidBidException extends AuctionException {
  public InvalidBidException(String message) {
    super(message);
  }
}
