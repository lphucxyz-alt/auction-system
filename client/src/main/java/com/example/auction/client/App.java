package com.example.auction.client;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** JavaFX entry point and scene/session lifecycle owner. */
public final class App extends Application {
  private Stage stage;
  private AuctionConnection connection;
  private MainController main;

  @Override
  public void start(Stage stage) throws Exception {
    this.stage = stage;
    loadIcons(stage);
    showLogin();
    stage.show();
  }

  private static void loadIcons(Stage stage) {
    for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
      var resource = App.class.getResource("avatar-" + size + ".png");
      if (resource != null) {
        stage.getIcons().add(new Image(resource.toExternalForm()));
      }
    }
    var fallback = App.class.getResource("avatar.png");
    if (fallback != null && stage.getIcons().isEmpty()) {
      stage.getIcons().add(new Image(fallback.toExternalForm()));
    }
  }

  /** Updates the window and taskbar title to reflect the current user and role. */
  public void updateTitle(String username, String role) {
    if (stage == null) {
      return;
    }
    String baseTitle = "Hệ thống đấu giá trực tuyến";
    if (username != null && !username.isBlank()) {
      String trimmed = username.trim();
      if (role != null && !role.isBlank()) {
        stage.setTitle(trimmed + " [" + roleDisplayName(role) + "] — " + baseTitle);
      } else {
        stage.setTitle(trimmed + " — Đăng nhập | " + baseTitle);
      }
    } else {
      stage.setTitle("Đăng nhập — " + baseTitle);
    }
  }

  private static String roleDisplayName(String role) {
    return switch (role) {
      case "SELLER" -> "Người bán";
      case "ADMIN" -> "Quản trị";
      default -> "Người mua";
    };
  }

  private void scene(Parent root, double width, double height) {
    stage.setMinWidth(width < 1100 ? 900 : 1000);
    stage.setMinHeight(680);
    Scene scene = new Scene(root, width, height);
    stage.setScene(scene);
    stage.sizeToScene();
  }

  /** Closes the current session and returns to the authentication screen with optional feedback. */
  public void showLogin(String feedbackMessage) throws java.io.IOException {
    if (main != null) {
      main.dispose();
      main = null;
    }
    if (connection != null) {
      connection.close();
      connection = null;
    }
    FXMLLoader loader = new FXMLLoader(App.class.getResource("login.fxml"));
    Parent root = loader.load();
    LoginController controller = loader.getController();
    controller.configure(this);
    if (feedbackMessage != null && !feedbackMessage.isBlank()) {
      controller.feedback(feedbackMessage, true);
    }
    scene(root, 980, 740);
    String initialUser = System.getProperty("auction.username", "").trim();
    updateTitle(initialUser, null);
  }

  /** Closes the current session and returns to the authentication screen. */
  public void showLogin() throws java.io.IOException {
    showLogin(null);
  }

  /** Transitions from authentication to the live auction workspace. */
  public void loggedIn(AuctionConnection connection, JsonObject user) throws java.io.IOException {
    this.connection = connection;
    String uname = user != null && user.has("username") ? user.get("username").getAsString() : "";
    String urole = user != null && user.has("role") ? user.get("role").getAsString() : "";
    updateTitle(uname, urole);
    FXMLLoader loader = new FXMLLoader(App.class.getResource("main.fxml"));
    Parent root = loader.load();
    main = loader.getController();
    main.configure(this, connection, user);
    scene(root, 1180, 780);
  }

  @Override
  public void stop() {
    if (main != null) {
      main.dispose();
    }
    if (connection != null) {
      connection.close();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
