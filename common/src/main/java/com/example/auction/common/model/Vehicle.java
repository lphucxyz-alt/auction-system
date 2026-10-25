package com.example.auction.common.model;

public class Vehicle extends Item {
  private final String brand;
  private final int mileageKm;

  public Vehicle(String name, String description, long startingPrice,
      String brand, int mileageKm) {
    super(name, description, startingPrice);
    this.brand = brand;
    this.mileageKm = mileageKm;
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
