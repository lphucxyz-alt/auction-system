package com.example.auction.server;

import com.example.auction.common.model.Item;
import com.example.auction.common.model.ItemFactory;
import com.example.auction.common.model.ItemType;

public class Demo {
  public static void main(String[] args) {
    AuctionManager m1 = AuctionManager.getInstance();
    AuctionManager m2 = AuctionManager.getInstance();
    System.out.println("Cùng một đối tượng? " + (m1 == m2));

    m1.addItem(ItemFactory.create(ItemType.ELECTRONICS, "iPhone 15", "Mới 99%", 15_000_000, "Apple", 6));
    m1.addItem(ItemFactory.create(ItemType.ART, "Hoa sen", "Sơn dầu", 8_000_000, "Bùi Xuân Phái", 1975));
    m1.addItem(ItemFactory.create(ItemType.VEHICLE, "Honda SH", "Xe chính chủ", 50_000_000, "Honda", 12000));

    for (Item item : m2.getItems()) {
      System.out.println(item.printInfo()); // đa hình
    }
  }
}
