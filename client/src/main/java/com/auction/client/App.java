package com.auction.client;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
  @Override
  public void start(Stage stage) {
    Label label = new Label("Online Auction System - Client");
    stage.setScene(new Scene(new StackPane(label), 640, 400));
    stage.setTitle("Auction Client");
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
