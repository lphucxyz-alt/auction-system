package com.example.auction.server;

import com.example.auction.server.dao.FileStore;
import com.example.auction.server.network.AuctionServer;
import java.io.IOException;
import java.nio.file.Path;

/** Singleton quản lý vòng đời server đấu giá và kho dữ liệu trong một tiến trình. */
public final class AuctionManager implements AutoCloseable {
  private static final AuctionManager INSTANCE = new AuctionManager();
  private AuctionServer server;
  private FileStore store;

  private AuctionManager() {}

  public static AuctionManager getInstance() {
    return INSTANCE;
  }

  /** Mở kho, khởi động server và trả về cổng thực tế đang lắng nghe. */
  public synchronized int start(
      Path directory, String host, int port, boolean demo, String adminName, String adminPassword)
      throws IOException {
    if (server != null) {
      throw new IllegalStateException("Server đấu giá đã chạy trong tiến trình này");
    }
    FileStore openedStore = new FileStore(directory);
    AuctionServer startedServer = null;
    try {
      AuctionApplication app = new AuctionApplication(openedStore);
      if (demo) {
        app.seedDemo();
      }
      if (adminName != null && adminPassword != null) {
        app.bootstrap(adminName, adminPassword);
      }
      startedServer = new AuctionServer(app, host, port);
      startedServer.start();
      store = openedStore;
      server = startedServer;
      return server.getPort();
    } catch (IOException | RuntimeException startupError) {
      try (openedStore) {
        if (startedServer != null) {
          startedServer.close();
        }
      } catch (IOException cleanupError) {
        startupError.addSuppressed(cleanupError);
      }
      throw startupError;
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (server == null) {
      return;
    }
    FileStore openedStore = store;
    try (openedStore) {
      server.close();
    } finally {
      server = null;
      store = null;
    }
  }
}
