package com.example.auction.client;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point and scene/session lifecycle owner. */
public final class App extends Application {
  private Stage stage;
  private AuctionConnection connection;
  private MainController main;

  @Override
  public void start(Stage stage) throws Exception {
    this.stage = stage;
    stage.setTitle("GAVEL • Sàn đấu giá trực tuyến");
    stage.setMinWidth(1080);
    stage.setMinHeight(720);
    showLogin();
    stage.show();
  }

  private void scene(Parent root, double width, double height) {
    Scene scene = new Scene(root, width, height);
    scene.getStylesheets().add(App.class.getResource("auction.css").toExternalForm());
    stage.setScene(scene);
  }

  /** Closes the current session and returns to the authentication screen. */
  public void showLogin() throws java.io.IOException {
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
    loader.<LoginController>getController().configure(this);
    scene(root, 1120, 760);
  }

  /** Transitions from authentication to the live auction workspace. */
  public void loggedIn(AuctionConnection connection, JsonObject user) throws java.io.IOException {
    this.connection = connection;
    FXMLLoader loader = new FXMLLoader(App.class.getResource("main.fxml"));
    Parent root = loader.load();
    main = loader.getController();
    main.configure(this, connection, user);
    scene(root, 1280, 820);
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
