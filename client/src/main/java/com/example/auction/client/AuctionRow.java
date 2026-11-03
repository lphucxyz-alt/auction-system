package com.example.auction.client;

import com.google.gson.JsonObject;

/** Client view model owns a versioned, immutable-by-convention server snapshot. */
public record AuctionRow(JsonObject json) {
  public String text(String key) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
  }

  public String id() {
    return text("id");
  }

  public long version() {
    return json.get("version").getAsLong();
  }

  public long price() {
    return json.get("currentPrice").getAsLong();
  }

  public String name() {
    return text("name");
  }

  public String status() {
    return text("status");
  }
}
