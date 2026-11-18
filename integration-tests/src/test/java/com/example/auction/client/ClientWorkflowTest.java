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
import java.time.Instant;
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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
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
  void fxmlLoginBidRealtimeHistoryAndRoleScreens() throws Exception {
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
                stage.getScene().getRoot().lookupAll(".button").stream()
                    .filter(node -> node instanceof Button b && "bidder1".equals(b.getUserData()))
                    .map(node -> (Button) node)
                    .findFirst()
                    .orElseThrow()
                    .fire();
                assertEquals(
                    "bidder1", ((TextField) stage.getScene().lookup("#username")).getText());
                assertEquals(
                    "DemoBidder123!",
                    ((PasswordField) stage.getScene().lookup("#password")).getText());
                assertTrue(stage.getScene().lookup("#table") == null);
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
                Parent root = stage.getScene().getRoot();
                stage.getScene().setRoot(new javafx.scene.layout.Pane());
                stage.setScene(new Scene(root, 1000, 700));
                stage.sizeToScene();
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                assertEquals(1000, stage.getScene().getWidth());
                assertEquals(700, stage.getScene().getHeight());
                var button = stage.getScene().lookup("#bidButton");
                assertTrue(
                    button.getBoundsInParent().getMaxX()
                        <= button.getParent().getLayoutBounds().getWidth() + 1);
                return null;
              });
          screenshot(stage, "auction-compact.png");
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
          Stage sellerPreview =
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
                    ((TextField) form.lookup("#startingPrice")).setText("1000");
                    JsonObject draft = controller.payload();
                    assertTrue(draft.has("startDelaySeconds"));
                    Instant start = Instant.now().plusSeconds(300);
                    draft.addProperty("startTime", start.toString());
                    draft.addProperty(
                        "endTime", start.plusSeconds(180).plusMillis(25).toString());
                    controller.configure(new AuctionRow(draft));
                    assertFalse(
                        controller.payload().has("startDelaySeconds"),
                        "Sửa nháp phải giữ lịch mở nếu không chỉnh trường lịch");
                    assertFalse(controller.payload().has("durationSeconds"));
                    ((TextField) form.lookup("#delay")).setText("900");
                    assertEquals(900, controller.payload().get("startDelaySeconds").getAsInt());
                    Stage preview = new Stage();
                    preview.setScene(new Scene(form));
                    preview.show();
                    return preview;
                  });
          try {
            screenshot(sellerPreview, "seller.png");
          } finally {
            fx(
                () -> {
                  sellerPreview.close();
                  return null;
                });
          }
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
                  return fx(
                      () ->
                          stage.getScene().lookup("#usersButton") != null
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
                assertTrue(stage.getScene().lookup("#usersButton").isVisible());
                assertTrue(stage.getScene().lookup("#createButton").isVisible());
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
                assertTrue(stage.getScene().lookup("#editButton").isDisabled());
                assertTrue(stage.getScene().lookup("#deleteButton").isDisabled());
                assertTrue(stage.getScene().lookup("#startButton").isDisabled());
                assertFalse(stage.getScene().lookup("#cancelButton").isDisabled());
                return null;
              });
          screenshot(stage, "admin.png");
          fx(
              () -> {
                ((Button) stage.getScene().lookup("#usersButton")).fire();
                return null;
              });
          awaitFx(() -> openDialog() != null);
          fx(
              () -> {
                DialogPane dialog = openDialog();
                assertEquals("Tài khoản hệ thống", dialog.getHeaderText());
                assertTrue(dialog.getContentText().contains("bidder2"));
                confirmDialog(dialog);
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
  @SuppressWarnings("unchecked")
  void historySortsByTheActualBidTimeAcrossMonths() throws Exception {
    fx(
        () -> {
          FXMLLoader loader = new FXMLLoader(App.class.getResource("main.fxml"));
          loader.load();
          var history = (TableView<JsonObject>) loader.getNamespace().get("history");
          JsonObject latest = new JsonObject();
          latest.addProperty("time", "2026-12-01T12:00:00Z");
          JsonObject earlier = new JsonObject();
          earlier.addProperty("time", "2026-11-30T12:00:00Z");
          history.getItems().setAll(latest, earlier);
          var time = history.getColumns().getLast();
          time.setSortType(TableColumn.SortType.ASCENDING);
          history.getSortOrder().add(time);
          assertEquals(earlier, history.getItems().getFirst());
          return null;
        });
  }

  @Test
  void sellerCreatesEditsAndDeletesThroughTheRealGui() throws Exception {
    try (FileStore store = new FileStore(data)) {
      AuctionApplication model = new AuctionApplication(store);
      model.seedDemo();
      try (AuctionServer server = new AuctionServer(model, "127.0.0.1", 0)) {
        server.start();
        System.setProperty("auction.port", Integer.toString(server.getPort()));
        App app = new App();
        Stage stage =
            fx(
                () -> {
                  Stage window = new Stage();
                  app.start(window);
                  ((TextField) window.getScene().lookup("#username")).setText("seller");
                  ((PasswordField) window.getScene().lookup("#password"))
                      .setText("DemoSeller123!");
                  ((Button) window.getScene().lookup("#login")).fire();
                  return window;
                });
        try {
          awaitFx(() -> stage.getScene().lookup("#createButton") != null);
          Platform.runLater(() -> ((Button) stage.getScene().lookup("#createButton")).fire());
          awaitFx(() -> openDialog() != null);
          fx(
              () -> {
                DialogPane dialog = openDialog();
                ((TextField) dialog.lookup("#name")).setText("Gói kiểm thử Seller");
                ((TextField) dialog.lookup("#startingPrice")).setText("1000");
                confirmDialog(dialog);
                return null;
              });
          awaitFx(() -> findLot(stage, "Gói kiểm thử Seller") != null);
          fx(
              () -> {
                var table = auctionTable(stage);
                table.getSelectionModel().select(findLot(stage, "Gói kiểm thử Seller"));
                return null;
              });
          final String scheduledStart =
              fx(() -> findLot(stage, "Gói kiểm thử Seller").text("startTime"));
          Platform.runLater(() -> ((Button) stage.getScene().lookup("#editButton")).fire());
          awaitFx(() -> openDialog() != null);
          fx(
              () -> {
                DialogPane dialog = openDialog();
                ((TextField) dialog.lookup("#name")).setText("Phiên đã đổi tên");
                confirmDialog(dialog);
                return null;
              });
          awaitFx(() -> findLot(stage, "Phiên đã đổi tên") != null);
          fx(
              () -> {
                assertEquals(scheduledStart, findLot(stage, "Phiên đã đổi tên").text("startTime"));
                return null;
              });
          Platform.runLater(() -> ((Button) stage.getScene().lookup("#deleteButton")).fire());
          awaitFx(() -> openDialog() != null);
          fx(
              () -> {
                confirmDialog(openDialog());
                return null;
              });
          awaitFx(() -> findLot(stage, "Phiên đã đổi tên") == null);
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

  private static void awaitFx(Callable<Boolean> condition) throws Exception {
    await(
        () -> {
          try {
            return fx(condition);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static DialogPane openDialog() {
    return Window.getWindows().stream()
        .filter(Window::isShowing)
        .map(window -> window.getScene().getRoot())
        .filter(DialogPane.class::isInstance)
        .map(DialogPane.class::cast)
        .findFirst()
        .orElse(null);
  }

  private static void confirmDialog(DialogPane dialog) {
    var confirm =
        dialog.getButtonTypes().stream()
            .filter(button -> button.getButtonData() == ButtonData.OK_DONE)
            .findFirst()
            .orElseThrow();
    ((Button) dialog.lookupButton(confirm)).fire();
  }

  @SuppressWarnings("unchecked")
  private static TableView<AuctionRow> auctionTable(Stage stage) {
    return (TableView<AuctionRow>) stage.getScene().lookup("#table");
  }

  private static AuctionRow findLot(Stage stage, String name) {
    return auctionTable(stage).getItems().stream()
        .filter(row -> row.name().equals(name))
        .findFirst()
        .orElse(null);
  }

  @Test
  void registrationLogsInThroughServerAndUnavailableServerShowsUsefulError() throws Exception {
    try (FileStore store = new FileStore(data);
        AuctionServer server = new AuctionServer(new AuctionApplication(store), "127.0.0.1", 0)) {
      server.start();
      System.setProperty("auction.port", Integer.toString(server.getPort()));
      App app = new App();
      Stage stage =
          fx(
              () -> {
                Stage window = new Stage();
                app.start(window);
                ((TextField) window.getScene().lookup("#username")).setText("new_bidder");
                ((PasswordField) window.getScene().lookup("#password")).setText("NewBidder123!");
                ((Button) window.getScene().lookup("#register")).fire();
                return window;
              });
      try {
        await(
            () -> {
              try {
                return fx(
                    () ->
                        ((Label) stage.getScene().lookup("#feedback"))
                            .getText()
                            .startsWith("Đăng ký thành công"));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
        fx(
            () -> {
              assertEquals("", ((PasswordField) stage.getScene().lookup("#password")).getText());
              assertTrue(stage.getScene().lookup("#table") == null);
              ((PasswordField) stage.getScene().lookup("#password")).setText("NewBidder123!");
              ((Button) stage.getScene().lookup("#login")).fire();
              return null;
            });
        await(
            () -> {
              try {
                return fx(
                    () ->
                        stage.getScene().lookup("#identity") instanceof Label label
                            && label.getText().equals("new_bidder / Người mua"));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
        server.close();
        fx(
            () -> {
              app.showLogin();
              ((TextField) stage.getScene().lookup("#username")).setText("new_bidder");
              ((PasswordField) stage.getScene().lookup("#password")).setText("NewBidder123!");
              ((Button) stage.getScene().lookup("#login")).fire();
              return null;
            });
        await(
            () -> {
              try {
                return fx(
                    () ->
                        ((Label) stage.getScene().lookup("#feedback"))
                            .getText()
                            .startsWith("Không kết nối được server"));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
        fx(
            () -> {
              assertFalse(stage.getScene().lookup("#login").isDisabled());
              assertTrue(stage.getScene().lookup("#feedback").getStyleClass().contains("error"));
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
