package com.example.auction.common.model;

/** Artwork product with artist and creation year. */
public class Art extends Item {
  private final String artist;
  private final int year;

  /** Creates an instance with the supplied identity and configuration. */
  public Art(String name, String description, long startingPrice, String artist, int year) {
    super(name, description, startingPrice);
    this.artist = artist;
    this.year = year;
  }

  public String getArtist() {
    return artist;
  }

  public int getYear() {
    return year;
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
