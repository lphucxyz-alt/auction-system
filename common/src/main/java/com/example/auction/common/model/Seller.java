package com.example.auction.common.model;

public class Seller extends User {
  public Seller(String username, String passwordHash) {
    super(username, passwordHash);
  }

  @Override
  public String getRole() {
    return "SELLER";
  }
}
