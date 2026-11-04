package com.example.auction.client;

import com.example.auction.client.protocol.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Live auction MVC controller with versioned socket updates and local filtering. */
public final class MainController {
  @FXML private TableView<AuctionRow> table;
  @FXML private TableColumn<AuctionRow, String> nameColumn;
  @FXML private TableColumn<AuctionRow, String> priceColumn;
  @FXML private TableColumn<AuctionRow, String> statusColumn;
  @FXML private TableView<JsonObject> history;
  @FXML private TableColumn<JsonObject, String> bidderColumn;
  @FXML private TableColumn<JsonObject, String> bidAmountColumn;
  @FXML private TableColumn<JsonObject, String> timeColumn;
  @FXML private TextField search;
  @FXML private TextField amount;
  @FXML private TextField maxBid;
  @FXML private TextField increment;
  @FXML private ComboBox<String> statusFilter;
  @FXML private CheckBox mine;
  @FXML private Label identity;
  @FXML private Label category;
  @FXML private Label title;
  @FXML private Label description;
  @FXML private Label price;
  @FXML private Label leader;
  @FXML private Label timing;
  @FXML private Label count;
  @FXML private Label feedback;
  @FXML private Button createButton;
  @FXML private Button usersButton;
  @FXML private Button payButton;
  @FXML private Button bidButton;
  @FXML private VBox bidControls;
  @FXML private HBox sellerControls;
  @FXML private LineChart<String, Number> chart;
  private final Map<String, AuctionRow> rows = new LinkedHashMap<>();
  private final Set<String> deleted = new HashSet<>();
  private final ExecutorService requests =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "ui-requests");
            thread.setDaemon(true);
            return thread;
          });
  private AuctionRow selected;
  private JsonObject user;
  private App app;
  private AuctionConnection connection;
  private boolean rendering;
  private boolean disposed;
  private Timeline clock;
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

  @FXML
  private void initialize() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
    priceColumn.setComparator(MainController::compareMoney);
    bidAmountColumn.setComparator(MainController::compareMoney);
    priceColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().price())));
    statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(status(c.getValue().status())));
    bidderColumn.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(c.getValue().get("bidder").getAsString()));
    bidAmountColumn.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(money(c.getValue().get("amount").getAsLong())));
    timeColumn.setCellValueFactory(
        c ->
            new ReadOnlyStringWrapper(
                TIME.format(Instant.parse(c.getValue().get("time").getAsString()))));
    statusFilter.getItems().setAll("Tất cả", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
    statusFilter.setValue("Tất cả");
    search.textProperty().addListener((o, a, b) -> renderList());
    statusFilter.valueProperty().addListener((o, a, b) -> renderList());
    mine.selectedProperty().addListener((o, a, b) -> renderList());
    table
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, old, row) -> {
              if (rendering) {
                return;
              }
              if (selected != null) {
                request("UNSUBSCRIBE", id(selected.id()), result -> {});
              }
              selected = row;
              detail();
              if (row != null) {
                request("SUBSCRIBE", id(row.id()), this::accept);
              }
            });
    clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTiming()));
    clock.setCycleCount(Timeline.INDEFINITE);
  }

  /** Binds this controller to its application/session or existing product snapshot. */
  public void configure(App app, AuctionConnection connection, JsonObject user) {
    this.app = app;
    this.connection = connection;
    this.user = user;
    String role = user.get("role").getAsString();
    identity.setText(user.get("username").getAsString() + " / " + role);
    visible(createButton, !role.equals("BIDDER"));
    visible(usersButton, role.equals("ADMIN"));
    connection.onEvent(message -> Platform.runLater(() -> event(message)));
    connection.onDisconnect(
        error ->
            Platform.runLater(
                () -> {
                  if (!disposed) {
                    feedback.setText(
                        "Mất kết nối. Đăng xuất rồi kết nối lại; kết quả thao tác dở cần kiểm tra"
                            + " lại.");
                    table.setDisable(true);
                    bidControls.setDisable(true);
                    sellerControls.setDisable(true);
                    createButton.setDisable(true);
                    payButton.setDisable(true);
                  }
                }));
    detail();
    refresh();
    clock.play();
  }

  @FXML
  private void refresh() {
    loadPage(0, null);
  }

  private void loadPage(int offset, String afterId) {
    JsonObject page = new JsonObject();
    page.addProperty("offset", offset);
    if (afterId != null) {
      page.addProperty("afterId", afterId);
    }
    request(
        "LIST_AUCTIONS",
        page,
        result -> {
          for (JsonElement a : result.getAsJsonArray("auctions")) {
            merge(a.getAsJsonObject());
          }
          renderList();
          int next = result.get("nextOffset").getAsInt();
          if (next >= 0) {
            JsonElement cursor = result.get("nextAfterId");
            loadPage(next, cursor == null || cursor.isJsonNull() ? null : cursor.getAsString());
          } else {
            feedback.setText("Đã đồng bộ • sự kiện mới được cập nhật tự động");
          }
        });
  }

  @FXML
  private void fullHistory() {
    if (selected != null) {
      loadHistory(selected.id(), selected.version(), 0, new ArrayList<>());
    }
  }

  private void loadHistory(String auctionId, long version, int offset, List<JsonObject> bids) {
    JsonObject p = id(auctionId);
    p.addProperty("offset", offset);
    request(
        "GET_HISTORY",
        p,
        result -> {
          if (selected == null || !selected.id().equals(auctionId)) {
            return;
          }
          if (selected.version() != version) {
            feedback.setText(
                "Phiên vừa thay đổi. Lịch sử hiện tại được giữ; tải lại để xem đầy đủ.");
            return;
          }
          result.getAsJsonArray("history").forEach(b -> bids.add(b.getAsJsonObject()));
          int next = result.get("nextOffset").getAsInt();
          if (next >= 0) {
            loadHistory(auctionId, version, next, bids);
          } else {
            Collections.reverse(bids);
            history.getItems().setAll(bids);
            feedback.setText(
                "Đã tải " + bids.size() + " lượt. Realtime hiển thị 100 lượt gần nhất.");
          }
        });
  }

  private void event(Message event) {
    if (disposed) {
      return;
    }
    JsonObject a = event.getPayload().getAsJsonObject("auction");
    if (a == null) {
      return;
    }
    if (event.getType().equals("AUCTION_DELETED")) {
      String auctionId = a.get("id").getAsString();
      rows.remove(auctionId);
      deleted.add(auctionId);
      if (selected != null && selected.id().equals(auctionId)) {
        selected = null;
        detail();
      }
      renderList();
      return;
    }
    accept(event.getPayload());
    if (event.getType().equals("AUCTION_FINISHED")) {
      feedback.setText("Phiên đã kết thúc. Xem người thắng trong chi tiết.");
    }
  }

  private void accept(JsonObject payload) {
    if (payload.has("auction")) {
      merge(payload.getAsJsonObject("auction"));
      renderList();
    }
  }

  private void merge(JsonObject json) {
    AuctionRow row = new AuctionRow(json);
    if (deleted.contains(row.id())) {
      return;
    }
    AuctionRow previous = rows.get(row.id());
    if (previous != null && previous.version() >= row.version()) {
      return;
    }
    rows.put(row.id(), row);
    if (selected != null && selected.id().equals(row.id())) {
      selected = row;
      detail();
    }
  }

  private void renderList() {
    if (user == null) {
      return;
    }
    String query = search.getText().toLowerCase(Locale.ROOT);
    List<AuctionRow> filtered =
        rows.values().stream()
            .filter(
                a -> (a.name() + " " + a.text("seller")).toLowerCase(Locale.ROOT).contains(query))
            .filter(
                a ->
                    statusFilter.getValue().equals("Tất cả")
                        || a.status().equals(statusFilter.getValue()))
            .filter(
                a ->
                    !mine.isSelected()
                        || a.text("sellerId").equals(user.get("id").getAsString())
                        || a.text("leaderId").equals(user.get("id").getAsString()))
            .sorted(Comparator.comparing(AuctionRow::name))
            .toList();
    rendering = true;
    try {
      table.getItems().setAll(filtered);
      table.sort();
      if (selected != null) {
        AuctionRow same =
            filtered.stream().filter(a -> a.id().equals(selected.id())).findFirst().orElse(null);
        if (same != null) {
          table.getSelectionModel().select(same);
        } else {
          String previousId = selected.id();
          selected = null;
          detail();
          request("UNSUBSCRIBE", id(previousId), result -> {});
        }
      }
    } finally {
      rendering = false;
    }
    count.setText(filtered.size() + " phiên hiển thị / " + rows.size() + " tổng cộng");
  }

  private void detail() {
    boolean present = selected != null && user != null;
    visible(bidControls, present && user.get("role").getAsString().equals("BIDDER"));
    boolean owner =
        present
            && (user.get("role").getAsString().equals("ADMIN")
                || selected.text("sellerId").equals(user.get("id").getAsString()));
    visible(sellerControls, owner);
    visible(
        payButton,
        present
            && selected.status().equals("FINISHED")
            && selected.text("leaderId").equals(user.get("id").getAsString()));
    if (!present) {
      title.setText("Chọn phiên để xem chi tiết");
      category.setText("SÀN ĐẤU GIÁ");
      description.setText("");
      price.setText("— VND");
      leader.setText("");
      timing.setText("");
      history.getItems().clear();
      chart.getData().clear();
      return;
    }
    category.setText(selected.text("category") + " / " + status(selected.status()));
    title.setText(selected.name());
    description.setText(
        selected.text("description")
            + "\n"
            + selected.text("itemInfo")
            + "\nNgười bán: "
            + selected.text("seller"));
    price.setText(money(selected.price()) + " VND");
    String leading = selected.text("leader");
    leader.setText(
        leading.isEmpty()
            ? "Chưa có người đặt giá"
            : (selected.status().equals("FINISHED") || selected.status().equals("PAID")
                    ? "Người thắng: "
                    : "Đang dẫn đầu: ")
                + leading);
    bidControls.setDisable(!selected.status().equals("RUNNING"));
    List<JsonObject> bids = new ArrayList<>();
    selected.json().getAsJsonArray("history").forEach(b -> bids.add(b.getAsJsonObject()));
    List<JsonObject> reversed = new ArrayList<>(bids);
    Collections.reverse(reversed);
    history.getItems().setAll(reversed);
    history.sort();
    XYChart.Series<String, Number> series = new XYChart.Series<>();
    int index = 0;
    for (JsonObject bid : bids) {
      series
          .getData()
          .add(
              new XYChart.Data<>(
                  ++index + " · " + TIME.format(Instant.parse(bid.get("time").getAsString())),
                  bid.get("amount").getAsLong()));
    }
    chart.getData().setAll(List.of(series));
    updateTiming();
  }

  private void updateTiming() {
    if (selected == null) {
      return;
    }
    Instant end = Instant.parse(selected.text("endTime"));
    long seconds = Math.max(0, java.time.Duration.between(Instant.now(), end).getSeconds());
    timing.setText(
        "Bắt đầu "
            + TIME.format(Instant.parse(selected.text("startTime")))
            + " • Kết thúc "
            + TIME.format(end)
            + "\nCòn "
            + seconds / 60
            + " phút "
            + seconds % 60
            + " giây"
            + (selected.json().get("antiSniping").getAsBoolean() ? " • Anti-sniping bật" : ""));
  }

  @FXML
  private void bid() {
    if (selected == null) {
      return;
    }
    JsonObject p = id(selected.id());
    try {
      p.addProperty("amount", Long.parseLong(amount.getText().trim()));
    } catch (NumberFormatException e) {
      feedback.setText("Giá đặt phải là số nguyên VND, không dấu phân cách.");
      return;
    }
    request(
        "PLACE_BID",
        p,
        result -> {
          accept(result);
          amount.clear();
          feedback.setText(
              "Giá đã được server chấp nhận. Auto-bid có thể trả giá cao hơn ngay sau đó.");
        });
  }

  @FXML
  private void autoBid() {
    if (selected == null) {
      return;
    }
    JsonObject p = id(selected.id());
    try {
      p.addProperty("maxBid", Long.parseLong(maxBid.getText().trim()));
      p.addProperty("increment", Long.parseLong(increment.getText().trim()));
    } catch (NumberFormatException e) {
      feedback.setText("Trần và bước giá phải là số nguyên dương.");
      return;
    }
    request(
        "AUTO_BID",
        p,
        result -> {
          accept(result);
          feedback.setText("Đã bật auto-bid; trần được giữ riêng tại server.");
        });
  }

  @FXML
  private void removeAuto() {
    command("REMOVE_AUTO_BID", "Đã tắt auto-bid.");
  }

  @FXML
  private void start() {
    command("START_AUCTION", "Phiên đã bắt đầu.");
  }

  @FXML
  private void cancel() {
    if (confirm("Hủy phiên? Thao tác này không thể hoàn tác.")) {
      command("CANCEL_AUCTION", "Đã hủy phiên.");
    }
  }

  @FXML
  private void pay() {
    if (confirm("Xác nhận thanh toán mô phỏng? Không phát sinh giao dịch tiền thật.")) {
      command("PAY_AUCTION", "Đã xác nhận thanh toán mô phỏng.");
    }
  }

  @FXML
  private void delete() {
    if (selected == null) {
      return;
    }
    String deletingId = selected.id();
    if (!confirm("Xóa phiên nháp đã chọn?")) {
      return;
    }
    request(
        "DELETE_AUCTION",
        id(deletingId),
        result -> {
          String removed = result.get("deletedId").getAsString();
          rows.remove(removed);
          deleted.add(removed);
          if (selected != null && selected.id().equals(removed)) {
            selected = null;
          }
          renderList();
          detail();
          feedback.setText("Đã xóa nháp.");
        });
  }

  private void command(String type, String message) {
    if (selected != null) {
      request(
          type,
          id(selected.id()),
          result -> {
            accept(result);
            feedback.setText(message);
          });
    }
  }

  @FXML
  private void create() {
    form(null);
  }

  @FXML
  private void edit() {
    if (selected != null) {
      form(selected);
    }
  }

  private void form(AuctionRow row) {
    try {
      FXMLLoader loader = new FXMLLoader(App.class.getResource("seller.fxml"));
      final Parent content = loader.load();
      SellerController controller = loader.getController();
      controller.configure(row);
      Dialog<JsonObject> dialog = new Dialog<>();
      dialog.setTitle(row == null ? "Tạo phiên đấu giá" : "Sửa phiên nháp");
      dialog.initOwner(table.getScene().getWindow());
      dialog.getDialogPane().setContent(content);
      dialog
          .getDialogPane()
          .getStylesheets()
          .add(App.class.getResource("auction.css").toExternalForm());
      ButtonType save = new ButtonType("Lưu phiên", ButtonBar.ButtonData.OK_DONE);
      dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
      dialog
          .getDialogPane()
          .lookupButton(save)
          .addEventFilter(
              javafx.event.ActionEvent.ACTION,
              event -> {
                try {
                  controller.payload();
                } catch (IllegalArgumentException e) {
                  controller.showError(e.getMessage());
                  event.consume();
                }
              });
      dialog.setResultConverter(button -> button == save ? controller.payload() : null);
      dialog
          .showAndWait()
          .ifPresent(
              payload -> {
                if (row != null) {
                  payload.addProperty("auctionId", row.id());
                }
                request(
                    row == null ? "CREATE_AUCTION" : "UPDATE_AUCTION",
                    payload,
                    result -> {
                      accept(result);
                      feedback.setText("Đã lưu phiên. Server tự mở theo thời gian đã đặt.");
                    });
              });
    } catch (Exception e) {
      feedback.setText(LoginController.error(e));
    }
  }

  @FXML
  private void users() {
    request(
        "LIST_USERS",
        new JsonObject(),
        result -> {
          StringBuilder text = new StringBuilder();
          result
              .getAsJsonArray("users")
              .forEach(
                  u ->
                      text.append(u.getAsJsonObject().get("username").getAsString())
                          .append("  /  ")
                          .append(u.getAsJsonObject().get("role").getAsString())
                          .append('\n'));
          Alert alert = new Alert(Alert.AlertType.INFORMATION, text.toString());
          alert.setHeaderText("Tài khoản hệ thống");
          alert.show();
        });
  }

  @FXML
  private void logout() {
    try {
      app.showLogin();
    } catch (Exception e) {
      feedback.setText(e.getMessage());
    }
  }

  private void request(String type, JsonObject payload, Consumer<JsonObject> success) {
    if (connection == null || disposed) {
      return;
    }
    CompletableFuture.supplyAsync(() -> connection.request(type, payload), requests)
        .thenCompose(f -> f)
        .whenComplete(
            (result, error) ->
                Platform.runLater(
                    () -> {
                      if (disposed) {
                        return;
                      }
                      if (error != null) {
                        feedback.setText(LoginController.error(error));
                      } else {
                        success.accept(result);
                      }
                    }));
  }

  /** Stops local timers when leaving the auction workspace. */
  public void dispose() {
    disposed = true;
    requests.shutdownNow();
    if (clock != null) {
      clock.stop();
    }
  }

  private static void visible(javafx.scene.Node node, boolean show) {
    node.setVisible(show);
    node.setManaged(show);
  }

  private boolean confirm(String message) {
    Alert alert =
        new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    alert.initOwner(table.getScene().getWindow());
    return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  private static JsonObject id(String id) {
    JsonObject p = new JsonObject();
    p.addProperty("auctionId", id);
    return p;
  }

  private static int compareMoney(String left, String right) {
    // Giá hiển thị theo vi-VN dùng dấu chấm phân cách hàng nghìn.
    return Long.compare(
        Long.parseLong(left.replace(".", "")), Long.parseLong(right.replace(".", "")));
  }

  private static String money(long amount) {
    return String.format(Locale.forLanguageTag("vi-VN"), "%,d", amount);
  }

  private static String status(String code) {
    return switch (code) {
      case "OPEN" -> "Sắp mở";
      case "RUNNING" -> "Đang đấu";
      case "FINISHED" -> "Đã kết thúc";
      case "PAID" -> "Đã thanh toán";
      case "CANCELED" -> "Đã hủy";
      default -> code;
    };
  }
}
