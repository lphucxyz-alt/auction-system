package com.example.auction.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class ItemFactoryTest {
  @Test
  void createsEachProductWithItsCategorySpecificFields() {
    Electronics camera =
        assertInstanceOf(
            Electronics.class,
            ItemFactory.create(ItemType.ELECTRONICS, "Máy ảnh", "Máy cũ", 100, "Fuji", 12));
    assertEquals("Fuji", camera.getBrand());
    assertEquals(12, camera.getWarrantyMonths());
    assertEquals(100, camera.getStartingPrice());

    Art picture =
        assertInstanceOf(
            Art.class, ItemFactory.create(ItemType.ART, "Tranh", "Sơn dầu", 200, "Họa sĩ", 2005));
    assertEquals("Họa sĩ", picture.getArtist());
    assertEquals(2005, picture.getYear());

    Vehicle vehicle =
        assertInstanceOf(
            Vehicle.class,
            ItemFactory.create(ItemType.VEHICLE, "Xe", "Chính chủ", 300, "Honda", 12_000));
    assertEquals("Honda", vehicle.getBrand());
    assertEquals(12_000, vehicle.getMileageKm());
  }
}
