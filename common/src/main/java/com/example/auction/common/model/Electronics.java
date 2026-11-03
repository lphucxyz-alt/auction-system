package com.example.auction.common.model;

/** Electronic product with brand and warranty period. */
public class Electronics extends Item {
  private final String brand;
  private final int warrantyMonths;

  /** Creates an instance with the supplied identity and configuration. */
  public Electronics(
      String name, String description, long startingPrice, String brand, int warrantyMonths) {
    super(name, description, startingPrice);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
  }

  public String getBrand() {
    return brand;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
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
