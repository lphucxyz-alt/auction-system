package com.example.auction.client;

import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/** Validates the FXML product and scheduling form. */
public final class SellerController {
  @FXML private TextField name;
  @FXML private TextField startingPrice;
  @FXML private TextField extra1;
  @FXML private TextField extra2;
  @FXML private TextField delay;
  @FXML private TextField duration;
  @FXML private TextArea description;
  @FXML private ComboBox<String> category;
  @FXML private CheckBox anti;
  @FXML private Label error;

  @FXML
  private void initialize() {
    category.getItems().setAll("ELECTRONICS", "ART", "VEHICLE");
    category.setValue("ELECTRONICS");
    delay.setText("300");
    duration.setText("180");
    extra2.setText("0");
  }

  /** Binds this controller to its application/session or existing product snapshot. */
  public void configure(AuctionRow row) {
    if (row == null) {
      return;
    }
    name.setText(row.name());
    description.setText(row.text("description"));
    startingPrice.setText(row.text("startingPrice"));
    category.setValue(row.text("category"));
    extra1.setText(row.text("extra1"));
    extra2.setText(row.text("extra2"));
    delay.setText(
        Long.toString(
            Math.max(
                0,
                java.time.Duration.between(
                        java.time.Instant.now(), java.time.Instant.parse(row.text("startTime")))
                    .getSeconds())));
    duration.setText(
        Long.toString(
            java.time.Duration.between(
                    java.time.Instant.parse(row.text("startTime")),
                    java.time.Instant.parse(row.text("endTime")))
                .getSeconds()));
    anti.setSelected(row.json().get("antiSniping").getAsBoolean());
  }

  /** Validates form fields and creates the seller command payload. */
  public JsonObject payload() {
    if (name.getText().isBlank()
        || name.getText().length() > 120
        || description.getText().length() > 4000) {
      throw new IllegalArgumentException("Tên bắt buộc (≤120 ký tự), mô tả ≤4000 ký tự.");
    }
    JsonObject p = new JsonObject();
    p.addProperty("name", name.getText().trim());
    p.addProperty("description", description.getText());
    p.addProperty("category", category.getValue());
    p.addProperty("extra1", extra1.getText().trim());
    p.addProperty("startingPrice", number(startingPrice, 1, 1_000_000_000_000_000L));
    p.addProperty("extra2", number(extra2, 0, 10_000_000));
    p.addProperty("startDelaySeconds", number(delay, 0, 604800));
    p.addProperty("durationSeconds", number(duration, 1, 604800));
    p.addProperty("antiSniping", anti.isSelected());
    return p;
  }

  private long number(TextField field, long min, long max) {
    try {
      long n = Long.parseLong(field.getText().trim());
      if (n < min || n > max) {
        throw new NumberFormatException();
      }
      return n;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Nhập số nguyên trong khoảng " + min + "–" + max + ": " + field.getPromptText());
    }
  }

  public void showError(String message) {
    error.setText(message);
  }
}
