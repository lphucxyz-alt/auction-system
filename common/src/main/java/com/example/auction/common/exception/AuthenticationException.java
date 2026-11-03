package com.example.auction.common.exception;

/** Signals invalid authentication credentials. */
public class AuthenticationException extends AuctionException {
  public AuthenticationException(String message) {
    super(message);
  }
}
