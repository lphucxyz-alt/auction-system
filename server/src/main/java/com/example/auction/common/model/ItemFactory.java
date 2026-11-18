package com.example.auction.common.model;

/** Gọi factory method của loại sản phẩm được chọn. */
public final class ItemFactory {
  private ItemFactory() {}

  /**
   * extra1/extra2 tùy loại: ELECTRONICS = (hãng, số tháng bảo hành), ART = (họa sĩ, năm), VEHICLE =
   * (hãng, số km).
   */
  public static Item create(
      ItemType type,
      String name,
      String description,
      long startingPrice,
      String extra1,
      int extra2) {
    return type.createItem(name, description, startingPrice, extra1, extra2);
  }
}
