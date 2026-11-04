package com.example.auction.common.model;

/** Vehicle product with brand and mileage. */
public class Vehicle extends Item {
  private final String brand;
  private final int mileageKm;

  /** Creates an instance with the supplied identity and configuration. */
  public Vehicle(String name, String description, long startingPrice, String brand, int mileageKm) {
    super(name, description, startingPrice);
    this.brand = brand;
    this.mileageKm = mileageKm;
  }

  public String getBrand() {
    return brand;
  }

  public int getMileageKm() {
    return mileageKm;
  }

  @Override
  public String getCategory() {
    return "VEHICLE";
  }

  @Override
  public String printInfo() {
    return super.printInfo() + " | hãng " + brand + ", đã đi " + mileageKm + " km";
  }
}
