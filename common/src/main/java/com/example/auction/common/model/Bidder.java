package com.example.auction.common.model;

public class Bidder extends User {
  public Bidder(String username, String passwordHash) {
    super(username, passwordHash);
  }

  @Override
  public String getRole() {
    return "BIDDER";
  }
}
