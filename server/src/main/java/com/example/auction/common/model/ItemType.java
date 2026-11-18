package com.example.auction.common.model;

/** Mỗi loại sản phẩm cài đặt cách tạo đối tượng của riêng mình. */
public enum ItemType {
  ELECTRONICS {
    @Override
    Item createItem(String name, String description, long price, String brand, int warrantyMonths) {
      return new Electronics(name, description, price, brand, warrantyMonths);
    }
  },
  ART {
    @Override
    Item createItem(String name, String description, long price, String artist, int year) {
      return new Art(name, description, price, artist, year);
    }
  },
  VEHICLE {
    @Override
    Item createItem(String name, String description, long price, String brand, int mileageKm) {
      return new Vehicle(name, description, price, brand, mileageKm);
    }
  };

  abstract Item createItem(String name, String description, long price, String extra1, int extra2);
}
