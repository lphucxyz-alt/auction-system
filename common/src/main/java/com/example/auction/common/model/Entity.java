package com.example.auction.common.model;

import java.util.UUID;

/** Lớp cơ sở: mọi đối tượng trong hệ thống đều có id duy nhất. */
public abstract class Entity {
  private final String id;

  protected Entity() {
    this.id = UUID.randomUUID().toString();
  }

  public String getId() {
    return id;
  }

  /** Mỗi lớp con tự mô tả mình (đa hình). */
  public abstract String printInfo();
}
