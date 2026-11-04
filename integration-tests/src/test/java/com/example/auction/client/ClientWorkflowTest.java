package com.example.auction.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.client.protocol.JsonLines;
import com.example.auction.client.protocol.Message;
import com.example.auction.server.AuctionApplication;
import com.example.auction.server.dao.FileStore;
import com.example.auction.server.network.AuctionServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
          list.getAsJsonArray("auctions").asList().stream()
              .map(element -> element.getAsJsonObject())
              .filter(lot -> lot.get("category").getAsString().equals("ELECTRONICS"))
              .findFirst()
              .orElseThrow()
              .get("id")
              .getAsString();
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
                @SuppressWarnings("unchecked")
                var column =
                    (javafx.scene.control.TableColumn<AuctionRow, String>)
                        ((TableView<?>) stage.getScene().lookup("#table")).getColumns().get(1);
                assertTrue(
                    column.getComparator().compare("2.000", "10.000") < 0,
                    "Giá phải sắp xếp theo số, không theo chuỗi");
                return null;
              });
          fx(
              () -> {
                ((TextField) stage.getScene().lookup("#search")).setText("khong-co-phien-nay-xyz");
                assertTrue(((TableView<?>) stage.getScene().lookup("#table")).getItems().isEmpty());
                assertFalse(
                    stage.getScene().lookup("#bidControls").isVisible(),
                    "Không được giữ nút bid cho phiên bị lọc khỏi bảng");
                ((TextField) stage.getScene().lookup("#search")).clear();
                return null;
              });
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

  @Test
  void lateHistoryResponseDoesNotOverwriteNewerPushedBid() throws Exception {
    try (FileStore store = new FileStore(data);
        ServerSocket listener = new ServerSocket(0);
        var worker = Executors.newSingleThreadExecutor()) {
      AuctionApplication model = new AuctionApplication(store);
      model.seedDemo();
      var seller = model.login("seller", "DemoSeller123!");
      final var buyer = model.login("bidder1", "DemoBidder123!");
      var lots =
          model.execute(seller, "LIST_AUCTIONS", new JsonObject()).getAsJsonArray("auctions");
      String auctionId =
          lots.asList().stream()
              .map(e -> e.getAsJsonObject())
              .filter(lot -> lot.get("category").getAsString().equals("ELECTRONICS"))
              .findFirst()
              .orElseThrow()
              .get("id")
              .getAsString();
      JsonObject id = new JsonObject();
      id.addProperty("auctionId", auctionId);
      model.execute(seller, "START_AUCTION", id);
      JsonObject bid = id.deepCopy();
      bid.addProperty("amount", 8100000);
      final JsonObject old = model.execute(buyer, "PLACE_BID", bid);
      bid.addProperty("amount", 8200000);
      final JsonObject fresh =
          model.execute(model.login("bidder2", "DemoBidder123!"), "PLACE_BID", bid);
      var stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  peer.setSoTimeout(5000);
                  var input = new BufferedInputStream(peer.getInputStream());
                  var output =
                      new BufferedWriter(
                          new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
                  String line;
                  while ((line = JsonLines.read(input)) != null) {
                    Message request = Message.fromJson(line);
                    JsonObject result = new JsonObject();
                    if (request.getType().equals("LIST_AUCTIONS")) {
                      JsonArray array = new JsonArray();
                      array.add(old.getAsJsonObject("auction"));
                      result.add("auctions", array);
                      result.addProperty("nextOffset", -1);
                    } else if (request.getType().equals("GET_HISTORY")) {
                      // Giá mới đến trước, rồi phản hồi lịch sử cũ đến chậm sau đó.
                      output.write(new Message("NEW_BID", null, fresh).toJson());
                      output.newLine();
                      output.flush();
                      result.add(
                          "history", old.getAsJsonObject("auction").getAsJsonArray("history"));
                      result.addProperty("nextOffset", -1);
                      result.addProperty("total", 1);
                    } else {
                      result = old;
                    }
                    output.write(Message.result(request.getRequestId(), result).toJson());
                    output.newLine();
                    output.flush();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection connection =
          new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        MainController[] controller = new MainController[1];
        Stage stage =
            fx(
                () -> {
                  FXMLLoader loader = new FXMLLoader(App.class.getResource("main.fxml"));
                  Parent root = loader.load();
                  controller[0] = loader.getController();
                  Stage window = new Stage();
                  window.setScene(new Scene(root, 1180, 780));
                  controller[0].configure(
                      new App(), connection, AuctionApplication.userView(buyer));
                  window.show();
                  return window;
                });
        try {
          await(
              () -> {
                try {
                  return fx(
                      () ->
                          ((TableView<?>) stage.getScene().lookup("#table")).getItems().size()
                              == 1);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
          fx(
              () -> {
                ((TableView<?>) stage.getScene().lookup("#table"))
                    .getSelectionModel()
                    .selectFirst();
                stage.getScene().getRoot().lookupAll(".button").stream()
                    .filter(
                        node ->
                            node instanceof Button button
                                && button.getText().equals("Tải toàn bộ lịch sử"))
                    .map(node -> (Button) node)
                    .findFirst()
                    .orElseThrow()
                    .fire();
                return null;
              });
          await(
              () -> {
                try {
                  return fx(
                      () -> {
                        String feedback = ((Label) stage.getScene().lookup("#feedback")).getText();
                        return feedback.startsWith("Phiên vừa thay đổi")
                            || feedback.startsWith("Đã tải");
                      });
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
          fx(
              () -> {
                assertEquals(
                    2, ((TableView<?>) stage.getScene().lookup("#history")).getItems().size());
                return null;
              });
        } finally {
          fx(
              () -> {
                controller[0].dispose();
                stage.close();
                return null;
              });
        }
      }
      stub.get(5, TimeUnit.SECONDS);
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
