package com.example.auction.common.model;

public abstract class Item extends Entity {
  private final String name;
  private final String description;
  private final long startingPrice; // VND

  protected Item(String name, String description, long startingPrice) {
    if (startingPrice <= 0) {
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
