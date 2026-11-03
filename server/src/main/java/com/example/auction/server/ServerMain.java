package com.example.auction.server;

import com.example.auction.server.dao.FileStore;
import com.example.auction.server.network.AuctionServer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/** Command-line entry point for the durable multi-client socket server. */
public final class ServerMain {
  private ServerMain() {}

  /** Starts this executable using its documented arguments and configuration. */
  public static void main(String[] args) throws Exception {
    String host = System.getProperty("auction.host", "127.0.0.1");
    int port = Integer.parseInt(System.getProperty("auction.port", "5000"));
    FileStore store = new FileStore(Path.of(System.getProperty("auction.data", "data")));
    try {
      AuctionApplication app = new AuctionApplication(store);
      if (Arrays.asList(args).contains("--demo")) {
        app.seedDemo();
      }
      String adminName = System.getenv("AUCTION_ADMIN_USER");
      String adminPassword = System.getenv("AUCTION_ADMIN_PASSWORD");
      if (adminName != null && adminPassword != null) {
        app.bootstrap(adminName, adminPassword);
      }
      AuctionServer server = new AuctionServer(app, host, port);
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      server.close();
                      store.close();
                    } catch (Exception e) {
                      System.err.println(e.getMessage());
                    }
                  }));
      server.start();
      System.out.println("Auction server ready on " + host + ":" + server.getPort());
      new CountDownLatch(1).await();
    } catch (Exception e) {
      store.close();
      throw e;
    }
  }
}
