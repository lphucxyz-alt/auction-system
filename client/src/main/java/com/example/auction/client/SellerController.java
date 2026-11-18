package com.example.auction.client;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

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
  @FXML private Label error;
  private Long originalDelay;
  private Long originalDuration;

  @FXML
  private void initialize() {
    category.getItems().setAll("ELECTRONICS", "ART", "VEHICLE");
    category.setValue("ELECTRONICS");
    category.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(String value) {
            return switch (value == null ? "" : value) {
              case "ART" -> "Nghệ thuật";
              case "VEHICLE" -> "Phương tiện";
              default -> "Điện tử";
            };
          }

          @Override
          public String fromString(String value) {
            return value;
          }
        });
    category.valueProperty().addListener((o, old, value) -> categoryHints());
    categoryHints();
    delay.setText("300");
    duration.setText("180");
    extra2.setText("0");
  }

  private void categoryHints() {
    String value = category.getValue();
    extra1.setPromptText("ART".equals(value) ? "Tác giả" : "Hãng sản xuất");
    extra2.setPromptText(
        switch (value) {
          case "ART" -> "Năm sáng tác";
          case "VEHICLE" -> "Số km đã đi";
          default -> "Số tháng bảo hành";
        });
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
    Instant start = Instant.parse(row.text("startTime"));
    Instant end = Instant.parse(row.text("endTime"));
    originalDelay = Math.max(0, Duration.between(Instant.now(), start).toSeconds());
    delay.setText(originalDelay.toString());
    originalDuration = Duration.between(start, end).toSeconds();
    duration.setText(originalDuration.toString());
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
    p.addProperty("startingPrice", number(startingPrice, 1, 999_999_999_999_999L));
    p.addProperty("extra2", number(extra2, 0, 10_000_000));
    long delaySeconds = number(delay, 0, 604800);
    if (originalDelay == null || delaySeconds != originalDelay) {
      p.addProperty("startDelaySeconds", delaySeconds);
    }
    long durationSeconds = number(duration, 1, 604800);
    if (originalDuration == null || durationSeconds != originalDuration) {
      p.addProperty("durationSeconds", durationSeconds);
    }
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
