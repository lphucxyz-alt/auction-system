package com.example.auction.server;

import java.io.IOException;
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
    Path directory = Path.of(System.getProperty("auction.data", "data"));
    try (AuctionManager manager = AuctionManager.getInstance()) {
      int boundPort =
          manager.start(
              directory,
              host,
              port,
              Arrays.asList(args).contains("--demo"),
              System.getenv("AUCTION_ADMIN_USER"),
              System.getenv("AUCTION_ADMIN_PASSWORD"));
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      manager.close();
                    } catch (IOException e) {
                      System.err.println(e.getMessage());
                    }
                  }));
      System.out.println("Auction server ready on " + host + ":" + boundPort);
      new CountDownLatch(1).await();
    }
  }
}
