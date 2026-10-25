package com.example.auction.common.model;

/** Factory: gom logic "new" vào một chỗ. Thêm loại Item mới chỉ sửa ở đây. */
public final class ItemFactory {
  private ItemFactory() {}

  /**
   * extra1/extra2 tùy loại:
   * ELECTRONICS = (hãng, số tháng bảo hành), ART = (họa sĩ, năm), VEHICLE = (hãng, số km).
   */
  public static Item create(ItemType type, String name, String description,
      long startingPrice, String extra1, int extra2) {
    return switch (type) {
      case ELECTRONICS -> new Electronics(name, description, startingPrice, extra1, extra2);
      case ART -> new Art(name, description, startingPrice, extra1, extra2);
      case VEHICLE -> new Vehicle(name, description, startingPrice, extra1, extra2);
    };
  }
}
