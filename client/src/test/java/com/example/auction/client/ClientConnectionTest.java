package com.example.auction.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.client.protocol.JsonLines;
import com.example.auction.client.protocol.Message;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Kiểm tra lớp mạng của client bằng socket giả, không import mã server. */
class ClientConnectionTest {
  @Test
  void matchesOutOfOrderResponsesAndReceivesPush() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  peer.setSoTimeout(5000);
                  var in = new BufferedInputStream(peer.getInputStream());
                  var out =
                      new BufferedWriter(
                          new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
                  final Message first = Message.fromJson(JsonLines.read(in));
                  Message second = Message.fromJson(JsonLines.read(in));
                  write(out, new Message("AUCTION_UPDATED", null, new JsonObject()));
                  JsonObject data = new JsonObject();
                  data.addProperty("order", 2);
                  write(out, Message.result(second.getRequestId(), data));
                  data = new JsonObject();
                  data.addProperty("order", 1);
                  write(out, Message.result(first.getRequestId(), data));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client = new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        CountDownLatch event = new CountDownLatch(1);
        client.onEvent(
            message -> {
              if (message.getType().equals("AUCTION_UPDATED")) {
                event.countDown();
              }
            });
        CompletableFuture<JsonObject> one = client.request("FIRST", new JsonObject());
        CompletableFuture<JsonObject> two = client.request("SECOND", new JsonObject());
        assertEquals(1, one.get(5, TimeUnit.SECONDS).get("order").getAsInt());
        assertEquals(2, two.get(5, TimeUnit.SECONDS).get("order").getAsInt());
        assertTrue(event.await(5, TimeUnit.SECONDS));
        stub.get(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void disconnectCompletesPendingRequestWithAnError() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  peer.setSoTimeout(5000);
                  JsonLines.read(new BufferedInputStream(peer.getInputStream()));
                  // Đóng kết nối trước khi trả lời.
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client = new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        assertThrows(
            ExecutionException.class,
            () -> client.request("WAIT", new JsonObject()).get(5, TimeUnit.SECONDS));
        stub.get(5, TimeUnit.SECONDS);
      }
    }
  }

  private static void write(BufferedWriter out, Message message) throws Exception {
    out.write(message.toJson());
    out.newLine();
    out.flush();
  }
}
