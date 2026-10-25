package com.example.auction.common.model;

public class Electronics extends Item {
  private final String brand;
  private final int warrantyMonths;

  public Electronics(String name, String description, long startingPrice,
      String brand, int warrantyMonths) {
    super(name, description, startingPrice);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
  }

  @Override
  public String getCategory() {
    return "ELECTRONICS";
  }

  @Override
  public String printInfo() {
    return super.printInfo() + " | hãng " + brand + ", bảo hành " + warrantyMonths + " tháng";
  }
}
