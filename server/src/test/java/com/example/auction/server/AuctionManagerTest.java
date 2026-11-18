package com.example.auction.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuctionManagerTest {
  @TempDir Path data;

  @Test
  void singletonStartsAndRestartsTheRealAuctionServer() throws Exception {
    AuctionManager manager = AuctionManager.getInstance();
    assertSame(manager, AuctionManager.getInstance());

    try (manager) {
      int port = manager.start(data, "127.0.0.1", 0, true, null, null);
      assertThrows(
          IllegalStateException.class,
          () -> manager.start(data, "127.0.0.1", 0, true, null, null));
      assertDemoLogin(port);
    }

    try (manager) {
      int port = manager.start(data, "127.0.0.1", 0, false, null, null);
      assertDemoLogin(port);
    }
  }

  @Test
  void failedBindReleasesStoreForAnotherStart() throws Exception {
    try (AuctionManager manager = AuctionManager.getInstance();
        ServerSocket occupied = new ServerSocket(0)) {
      assertThrows(
          IOException.class,
          () -> manager.start(data, "127.0.0.1", occupied.getLocalPort(), true, null, null));
      assertDemoLogin(manager.start(data, "127.0.0.1", 0, false, null, null));
    }
  }

  private void assertDemoLogin(int port) throws Exception {
    try (var peer = new SocketIntegrationTest.Peer(port)) {
      JsonObject login = new JsonObject();
      login.addProperty("username", "bidder1");
      login.addProperty("password", "DemoBidder123!");
      assertEquals(
          "BIDDER", peer.ok("LOGIN", login).getAsJsonObject("user").get("role").getAsString());
      assertEquals(3, peer.ok("LIST_AUCTIONS", new JsonObject()).get("total").getAsInt());
    }
  }
}
