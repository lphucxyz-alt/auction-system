package com.example.auction.common.exception;

/** Base exception for domain rule violations. */
public class AuctionException extends RuntimeException {
  public AuctionException(String message) {
    super(message);
  }
}
