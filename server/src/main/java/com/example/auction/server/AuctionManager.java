package com.example.auction.server;

import com.example.auction.common.model.Item;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Singleton (kiểu holder): an toàn đa luồng, tạo đối tượng lười (khi cần). */
public final class AuctionManager {
  private final Map<String, Item> items = new ConcurrentHashMap<>();

  private AuctionManager() {}

  private static final class Holder {
    private static final AuctionManager INSTANCE = new AuctionManager();
  }

  public static AuctionManager getInstance() {
    return Holder.INSTANCE;
  }

  public void addItem(Item item) {
    items.put(item.getId(), item);
  }

  public Collection<Item> getItems() {
    return items.values();
  }
}
