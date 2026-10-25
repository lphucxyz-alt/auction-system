package com.example.auction.common.protocol;

/** Các loại thông điệp. Client gửi yêu cầu, Server trả RESULT/ERROR hoặc đẩy sự kiện. */
public final class MessageType {
  private MessageType() {}

  // Client -> Server
  public static final String REGISTER = "REGISTER";
  public static final String LOGIN = "LOGIN";
  public static final String LIST_AUCTIONS = "LIST_AUCTIONS";
  public static final String PLACE_BID = "PLACE_BID";
  public static final String SUBSCRIBE = "SUBSCRIBE";

  // Server -> Client (phản hồi)
  public static final String RESULT = "RESULT";
  public static final String ERROR = "ERROR";

  // Server -> Client (sự kiện đẩy xuống)
  public static final String NEW_BID = "NEW_BID";
  public static final String AUCTION_FINISHED = "AUCTION_FINISHED";
}
