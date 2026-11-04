package com.example.auction.client.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Đọc một dòng UTF-8 có giới hạn kích thước; dữ liệu mạng luôn là JSON. */
public final class JsonLines {
  public static final int MAX_FRAME_BYTES = 65_536;

  private JsonLines() {}

  public static String read(InputStream input) throws IOException {
    return read(input, MAX_FRAME_BYTES);
  }

  /** Reads a complete bounded UTF-8 frame, returning null only on clean EOF. */
  public static String read(InputStream input, int maximum) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    int next;
    while ((next = input.read()) != -1) {
      if (next == '\n') {
        return bytes.toString(StandardCharsets.UTF_8);
      }
      if (bytes.size() >= maximum) {
        throw new IOException("Thông điệp vượt giới hạn cho phép");
      }
      bytes.write(next);
    }
    if (bytes.size() != 0) {
      throw new IOException("Thông điệp bị ngắt giữa chừng");
    }
    return null;
  }
}
