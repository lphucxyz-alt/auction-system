package com.example.auction.common.model;

public class Admin extends User {
  public Admin(String username, String passwordHash) {
    super(username, passwordHash);
  }

  @Override
  public String getRole() {
    return "ADMIN";
  }
}
