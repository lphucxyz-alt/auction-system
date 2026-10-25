package com.example.auction.common.model;

public class Art extends Item {
  private final String artist;
  private final int year;

  public Art(String name, String description, long startingPrice, String artist, int year) {
    super(name, description, startingPrice);
    this.artist = artist;
    this.year = year;
  }

  @Override
  public String getCategory() {
    return "ART";
  }

  @Override
  public String printInfo() {
    return super.printInfo() + " | họa sĩ " + artist + ", năm " + year;
  }
}
