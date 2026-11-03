package com.example.auction.common.model;

/** Account permitted to manage its own auction lots. */
public class Seller extends User {
  public Seller(String username, String passwordHash) {
    super(username, passwordHash);
  }

  @Override
  public String getRole() {
    return "SELLER";
  }
}
