package com.example.auction.common.model;

/** Abstract product identity and validated common catalog data. */
public abstract class Item extends Entity {
  private final String name;
  private final String description;
  private final long startingPrice; // VND

  protected Item(String name, String description, long startingPrice) {
    if (name == null
        || name.isBlank()
        || name.length() > 120
        || description == null
        || description.length() > 4000) {
      throw new IllegalArgumentException("Tên/mô tả sản phẩm không hợp lệ");
    }
    if (startingPrice <= 0 || startingPrice > 1_000_000_000_000_000L) {
      throw new IllegalArgumentException("Giá khởi điểm phải > 0");
    }
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public long getStartingPrice() {
    return startingPrice;
  }

  public abstract String getCategory();

  @Override
  public String printInfo() {
    return "[" + getCategory() + "] " + name + " - khởi điểm " + startingPrice + " VND";
  }
}
