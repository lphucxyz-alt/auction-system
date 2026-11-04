package com.example.auction.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.server.AuctionApplication;
import com.example.auction.server.dao.FileStore;
import com.example.auction.server.network.AuctionServer;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientWorkflowTest {
  @TempDir Path data;
  private static final int WAIT_SECONDS = 10;

  @BeforeAll
  static void toolkit() throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          ready.countDown();
        });
    assertTrue(ready.await(WAIT_SECONDS, TimeUnit.SECONDS));
  }

  private static <T> T fx(Callable<T> task) throws Exception {
    FutureTask<T> future = new FutureTask<>(task);
    Platform.runLater(future);
    return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
    while (!condition.getAsBoolean() && System.nanoTime() < until) {
      Thread.sleep(30);
    }
    assertTrue(condition.getAsBoolean(), "Timed out waiting for real UI/server response");
  }

  @Test
  void fxmlLoginBidRealtimeChartAndRoleScreens() throws Exception {
    try (FileStore store = new FileStore(data)) {
      AuctionApplication model = new AuctionApplication(store);
      model.seedDemo();
      var seller = model.login("seller", "DemoSeller123!");
      JsonObject list = model.execute(seller, "LIST_AUCTIONS", new JsonObject());
      String auctionId =
          list.getAsJsonArray("auctions").get(0).getAsJsonObject().get("id").getAsString();
      JsonObject id = new JsonObject();
      id.addProperty("auctionId", auctionId);
      model.execute(seller, "START_AUCTION", id);
      try (AuctionServer server = new AuctionServer(model, "127.0.0.1", 0)) {
        server.start();
        System.setProperty("auction.port", Integer.toString(server.getPort()));
        App app = new App();
        Stage stage =
            fx(
                () -> {
                  Stage s = new Stage();
                  app.start(s);
                  return s;
                });
        try {
          screenshot(stage, "login.png");
          fx(
              () -> {
                ((TextField) stage.getScene().lookup("#username")).setText("bidder1");
                ((PasswordField) stage.getScene().lookup("#password")).setText("DemoBidder123!");
                ((Button) stage.getScene().lookup("#login")).fire();
                return null;
              });
          await(
              () -> {
                try {
                  return fx(
                      () ->
                          stage.getScene().lookup("#table") != null
                              && ((TableView<?>) stage.getScene().lookup("#table"))
                                      .getItems()
                                      .size()
                                  == 3);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
          fx(
              () -> {
                @SuppressWarnings("unchecked")
                TableView<AuctionRow> table =
                    (TableView<AuctionRow>) stage.getScene().lookup("#table");
                table
                    .getSelectionModel()
                    .select(
                        table.getItems().stream()
                            .filter(a -> a.id().equals(auctionId))
                            .findFirst()
                            .orElseThrow());
                assertFalse(stage.getScene().lookup("#createButton").isVisible());
                ((TextField) stage.getScene().lookup("#amount")).setText("8100000");
                ((Button) stage.getScene().lookup("#bidButton")).fire();
                return null;
              });
          await(
              () -> {
                try {
                  return fx(
                      () ->
                          ((TableView<?>) stage.getScene().lookup("#history")).getItems().size()
                              == 1);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
          fx(
              () -> {
                assertEquals(
                    1,
                    ((LineChart<?, ?>) stage.getScene().lookup("#chart"))
                        .getData()
                        .getFirst()
                        .getData()
                        .size());
                return null;
              });
          // A second socket drives a pushed change; GUI updates with no refresh request.
          try (AuctionConnection competitor =
              new AuctionConnection("127.0.0.1", server.getPort())) {
            JsonObject credentials = new JsonObject();
            credentials.addProperty("username", "bidder2");
            credentials.addProperty("password", "DemoBidder123!");
            competitor.request("LOGIN", credentials).get(WAIT_SECONDS, TimeUnit.SECONDS);
            JsonObject bid = id.deepCopy();
            bid.addProperty("amount", 8200000);
            competitor.request("PLACE_BID", bid).get(WAIT_SECONDS, TimeUnit.SECONDS);
            await(
                () -> {
                  try {
                    return fx(
                        () ->
                            ((TableView<?>) stage.getScene().lookup("#history")).getItems().size()
                                == 2);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
          }
          screenshot(stage, "auction-live.png");
          fx(
              () -> {
                FXMLLoader loader = new FXMLLoader(App.class.getResource("seller.fxml"));
                Parent form = loader.load();
                SellerController controller = loader.getController();
                controller.configure(null);
                ((TextField) form.lookup("#name")).setText("Test product");
                ((TextField) form.lookup("#startingPrice")).setText("1000");
                assertEquals("Test product", controller.payload().get("name").getAsString());
                assertEquals(1000, controller.payload().get("startingPrice").getAsLong());
                ((TextField) form.lookup("#startingPrice")).setText("invalid");
                assertThrows(IllegalArgumentException.class, controller::payload);
                return null;
              });
          fx(
              () -> {
                app.showLogin();
                return null;
              });
          fx(
              () -> {
                ((TextField) stage.getScene().lookup("#username")).setText("admin");
                ((PasswordField) stage.getScene().lookup("#password")).setText("DemoAdmin123!");
                ((Button) stage.getScene().lookup("#login")).fire();
                return null;
              });
          await(
              () -> {
                try {
                  return fx(() -> stage.getScene().lookup("#usersButton") != null);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
          fx(
              () -> {
                assertTrue(stage.getScene().lookup("#usersButton").isVisible());
                assertTrue(stage.getScene().lookup("#createButton").isVisible());
                return null;
              });
        } finally {
          fx(
              () -> {
                app.stop();
                stage.close();
                return null;
              });
        }
      }
    }
  }

  @Test
  void pendingRequestsFailWhenServerDisconnects() throws Exception {
    try (FileStore store = new FileStore(data)) {
      AuctionApplication model = new AuctionApplication(store);
      try (AuctionServer server = new AuctionServer(model, "127.0.0.1", 0)) {
        server.start();
        try (AuctionConnection client = new AuctionConnection("127.0.0.1", server.getPort())) {
          JsonObject invalid = new JsonObject();
          ExecutionException e =
              assertThrows(
                  ExecutionException.class,
                  () -> client.request("LIST_AUCTIONS", invalid).get(5, TimeUnit.SECONDS));
          assertInstanceOf(AuctionConnection.RemoteException.class, e.getCause());
          server.close();
          assertThrows(
              ExecutionException.class,
              () -> client.request("LIST_AUCTIONS", invalid).get(5, TimeUnit.SECONDS));
        }
      }
    }
  }

  private static void screenshot(Stage stage, String filename) throws Exception {
    WritableImage image =
        fx(
            () -> {
              stage.getScene().getRoot().applyCss();
              stage.getScene().getRoot().layout();
              return stage.getScene().snapshot(null);
            });
    BufferedImage buffered =
        new BufferedImage(
            (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < buffered.getHeight(); y++) {
      for (int x = 0; x < buffered.getWidth(); x++) {
        buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
      }
    }
    Path directory = Path.of("target", "screenshots");
    java.nio.file.Files.createDirectories(directory);
    ImageIO.write(buffered, "png", directory.resolve(filename).toFile());
  }
}
