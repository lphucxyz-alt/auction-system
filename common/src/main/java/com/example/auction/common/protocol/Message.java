package com.example.auction.common.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/** Một thông điệp trên dây: đúng một dòng JSON. */
public final class Message {
  private static final Gson GSON = new Gson(); // không pretty-print: luôn nằm trên 1 dòng

  private final String type;
  private final String requestId; // null với sự kiện Server đẩy xuống
  private final JsonObject payload;

  public Message(String type, String requestId, JsonObject payload) {
    this.type = type;
    this.requestId = requestId;
    this.payload = payload == null ? new JsonObject() : payload;
  }

  public static Message result(String requestId, JsonObject payload) {
    return new Message(MessageType.RESULT, requestId, payload);
  }

  public static Message error(String requestId, String code, String text) {
    JsonObject p = new JsonObject();
    p.addProperty("code", code);
    p.addProperty("message", text);
    return new Message(MessageType.ERROR, requestId, p);
  }

  public String getType() {
    return type;
  }

  public String getRequestId() {
    return requestId;
  }

  public JsonObject getPayload() {
    return payload;
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  /** Dòng JSON hỏng thì ném IllegalArgumentException để Server trả BAD_REQUEST. */
  public static Message fromJson(String line) {
    try {
      Message m = GSON.fromJson(line, Message.class);
      if (m == null || m.type == null) {
        throw new IllegalArgumentException("Thiếu trường type");
      }
      return m;
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("JSON không hợp lệ: " + e.getMessage());
    }
  }
}
