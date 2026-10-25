package com.example.auction.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MessageTest {
  @Test
  void roundTripKeepsFields() {
    JsonObject p = new JsonObject();
    p.addProperty("amount", 2_000_000L);
    Message back = Message.fromJson(new Message(MessageType.PLACE_BID, "7", p).toJson());

    assertEquals(MessageType.PLACE_BID, back.getType());
    assertEquals("7", back.getRequestId());
    assertEquals(2_000_000L, back.getPayload().get("amount").getAsLong());
  }

  @Test
  void jsonStaysOnOneLineEvenWithNewlineInText() {
    String json = Message.error("1", ErrorCode.BAD_REQUEST, "dòng 1\ndòng 2").toJson();
    assertFalse(json.contains("\n"));
  }

  @Test
  void rejectsGarbage() {
    assertThrows(IllegalArgumentException.class, () -> Message.fromJson("không phải json {{"));
    assertThrows(IllegalArgumentException.class, () -> Message.fromJson("{}"));
  }
}
