package com.example.auction.common.model;

public enum AuctionStatus {
  OPEN, RUNNING, FINISHED, PAID, CANCELED;

  /** Máy trạng thái: chỉ cho phép chuyển đúng thứ tự. */
  public boolean canTransitionTo(AuctionStatus next) {
    return switch (this) {
      case OPEN -> next == RUNNING || next == CANCELED;
      case RUNNING -> next == FINISHED || next == CANCELED;
      case FINISHED -> next == PAID || next == CANCELED;
      case PAID, CANCELED -> false;
    };
  }
}
