package com.example.auction.server;

/** Application failure with a stable protocol error code. */
public final class ApiException extends RuntimeException {
  private final String code;

  public ApiException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
