package com.example.auction.common.protocol;

/** Mỗi ngoại lệ của Server ứng với một mã lỗi mà Client hiểu và hiển thị. */
public final class ErrorCode {
  private ErrorCode() {}

  public static final String BAD_REQUEST = "BAD_REQUEST";
  public static final String AUTH_FAILED = "AUTH_FAILED";
  public static final String NOT_LOGGED_IN = "NOT_LOGGED_IN";
  public static final String INVALID_BID = "INVALID_BID";
  public static final String AUCTION_CLOSED = "AUCTION_CLOSED";
  public static final String INTERNAL = "INTERNAL";
}
