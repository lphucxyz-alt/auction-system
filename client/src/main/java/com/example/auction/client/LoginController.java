package com.example.auction.client;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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
    host.setText(System.getProperty("auction.host", "127.0.0.1"));
    port.setText(System.getProperty("auction.port", "5000"));
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
      feedback.setText("Cổng cần là số từ 1 đến 65535.");
      return;
    }
    if (user.isEmpty() || pass.isEmpty()) {
      feedback.setText("Nhập tên đăng nhập và mật khẩu.");
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("username", user);
    payload.addProperty("password", pass);
    payload.addProperty("role", role.getValue());
    login.setDisable(true);
    register.setDisable(true);
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
                    feedback.setText("Đăng ký thành công. Hãy đăng nhập.");
                  } else {
                    try {
                      app.loggedIn(connected, result.getAsJsonObject("user"));
                    } catch (Exception e) {
                      connected.close();
                      feedback.setText(e.getMessage());
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
                  feedback.setText(error(e));
                });
          }
        });
  }

  static String error(Throwable e) {
    while (e.getCause() != null) {
      e = e.getCause();
    }
    return e.getMessage() == null ? "Không thể hoàn tất yêu cầu" : e.getMessage();
  }
}
