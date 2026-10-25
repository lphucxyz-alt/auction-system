package com.example.auction.common.model;

public abstract class User extends Entity {
  private final String username;
  private final String passwordHash;

  protected User(String username, String passwordHash) {
    this.username = username;
    this.passwordHash = passwordHash;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public abstract String getRole();

  @Override
  public String printInfo() {
    return getRole() + ": " + username;
  }
}
