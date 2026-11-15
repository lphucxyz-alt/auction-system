package com.example.auction.client;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Authentication controller; networking runs outside the JavaFX thread. */
public final class LoginController {
  @FXML private TextField host;
  @FXML private TextField port;
  @FXML private TextField username;
  @FXML private PasswordField password;
  @FXML private ComboBox<String> role;
  @FXML private Button login;
  @FXML private Button register;
  @FXML private Label feedback;
  private App app;

  @FXML
  private void initialize() {
    role.getItems().setAll("BIDDER", "SELLER");
    role.setValue("BIDDER");
    role.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(String value) {
            return "SELLER".equals(value) ? "Người bán" : "Người mua";
          }

          @Override
          public String fromString(String value) {
            return value;
          }
        });
    host.setText(System.getProperty("auction.host", "127.0.0.1"));
    port.setText(System.getProperty("auction.port", "5000"));
    username.setText(System.getProperty("auction.username", ""));
  }

  public void configure(App app) {
    this.app = app;
  }

  @FXML
  private void login() {
    authenticate(false);
  }

  @FXML
  private void register() {
    authenticate(true);
  }

  @FXML
  private void chooseDemo(ActionEvent event) {
    if (login.isDisabled()) {
      return;
    }
    String account = ((Button) event.getSource()).getUserData().toString();
    username.setText(account);
    password.setText(
        switch (account) {
          case "seller" -> "DemoSeller123!";
          case "admin" -> "DemoAdmin123!";
          default -> "DemoBidder123!";
        });
    feedback("Đã điền tài khoản " + account + ". Bấm Đăng nhập để tiếp tục.", false);
    login.requestFocus();
  }

  private void feedback(String message, boolean error) {
    feedback.getStyleClass().removeAll("error", "success");
    feedback.getStyleClass().add(error ? "error" : "success");
    feedback.setText(message);
  }

  private void authenticate(boolean create) {
    final String address = host.getText().trim();
    final String user = username.getText().trim();
    final String pass = password.getText();
    int number;
    try {
      number = Integer.parseInt(port.getText().trim());
      if (number < 1 || number > 65535) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException e) {
      feedback("Cổng cần là số từ 1 đến 65535.", true);
      return;
    }
    if (address.isEmpty() || user.isEmpty() || pass.isEmpty()) {
      feedback("Nhập địa chỉ server, tên đăng nhập và mật khẩu.", true);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("username", user);
    payload.addProperty("password", pass);
    payload.addProperty("role", role.getValue());
    login.setDisable(true);
    register.setDisable(true);
    feedback.getStyleClass().removeAll("error", "success");
    feedback.setText("Đang kết nối…");
    CompletableFuture.runAsync(
        () -> {
          AuctionConnection connection = null;
          try {
            connection = new AuctionConnection(address, number);
            JsonObject result = connection.request(create ? "REGISTER" : "LOGIN", payload).get();
            AuctionConnection connected = connection;
            if (create) {
              connection.close();
            }
            Platform.runLater(
                () -> {
                  login.setDisable(false);
                  register.setDisable(false);
                  password.clear();
                  if (create) {
                    feedback("Đăng ký thành công. Nhập lại mật khẩu rồi bấm Đăng nhập.", false);
                  } else {
                    try {
                      app.loggedIn(connected, result.getAsJsonObject("user"));
                    } catch (Exception e) {
                      connected.close();
                      feedback(error(e), true);
                    }
                  }
                });
          } catch (Exception e) {
            if (connection != null) {
              connection.close();
            }
            Platform.runLater(
                () -> {
                  login.setDisable(false);
                  register.setDisable(false);
                  feedback(error(e), true);
                });
          }
        });
  }

  static String error(Throwable e) {
    while (e.getCause() != null) {
      e = e.getCause();
    }
    if (e instanceof java.net.ConnectException) {
      return "Không kết nối được server. Kiểm tra server đã chạy và địa chỉ/cổng đúng.";
    }
    if (e instanceof java.net.UnknownHostException) {
      return "Không tìm thấy địa chỉ server. Trên cùng máy, dùng 127.0.0.1.";
    }
    return e.getMessage() == null ? "Không thể hoàn tất yêu cầu" : e.getMessage();
  }
}
