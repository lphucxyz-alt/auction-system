package com.example.auction.server;

import com.example.auction.common.model.Item;
import com.example.auction.common.model.ItemFactory;
import com.example.auction.common.model.ItemType;
import java.util.List;

/** Console demonstration of product polymorphism and the factory method. */
public class Demo {
  /** Starts this executable using its documented arguments and configuration. */
  public static void main(String[] args) {
    List<Item> items =
        List.of(
            ItemFactory.create(
                ItemType.ELECTRONICS, "iPhone 15", "Mới 99%", 15_000_000, "Apple", 6),
            ItemFactory.create(
                ItemType.ART, "Hoa sen", "Sơn dầu", 8_000_000, "Bùi Xuân Phái", 1975),
            ItemFactory.create(
                ItemType.VEHICLE, "Honda SH", "Xe chính chủ", 50_000_000, "Honda", 12000));
    for (Item item : items) {
      System.out.println(item.printInfo());
    }
  }
}
