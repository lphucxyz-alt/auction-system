package com.example.auction.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void malformedErrorResponseCompletesRequestInsteadOfLeavingItPending() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  peer.setSoTimeout(5000);
                  Message request =
                      Message.fromJson(
                          JsonLines.read(new BufferedInputStream(peer.getInputStream())));
                  var out =
                      new BufferedWriter(
                          new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
                  write(out, new Message("ERROR", request.getRequestId(), new JsonObject()));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client = new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        assertThrows(
            ExecutionException.class,
            () -> client.request("MALFORMED", new JsonObject()).get(2, TimeUnit.SECONDS));
        stub.get(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void disconnectListenerAttachedAfterFailureStillRuns() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  peer.setSoTimeout(5000);
                  JsonLines.read(new BufferedInputStream(peer.getInputStream()));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client = new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        assertThrows(
            ExecutionException.class,
            () -> client.request("WAIT", new JsonObject()).get(3, TimeUnit.SECONDS));
        stub.get(5, TimeUnit.SECONDS);
        CountDownLatch notified = new CountDownLatch(1);
        client.onDisconnect(error -> notified.countDown());
        assertTrue(
            notified.await(1, TimeUnit.SECONDS),
            "Mất kết nối trước lúc gắn listener phải được báo lại");
      }
    }
  }

  @Test
  void closeDoesNotWaitForBlockedWriter() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newFixedThreadPool(3)) {
      listener.setReceiveBufferSize(1024);
      Future<AuctionConnection> connecting =
          worker.submit(() -> new AuctionConnection("127.0.0.1", listener.getLocalPort()));
      try (Socket peer = listener.accept();
          AuctionConnection client = connecting.get(5, TimeUnit.SECONDS)) {
        var field = AuctionConnection.class.getDeclaredField("socket");
        field.setAccessible(true);
        ((Socket) field.get(client)).setSendBufferSize(1024);
        peer.setReceiveBufferSize(1024);
        AtomicReference<Thread> senderThread = new AtomicReference<>();
        AtomicBoolean stop = new AtomicBoolean();
        JsonObject large = new JsonObject();
        large.addProperty("data", "x".repeat(60000));
        Future<?> sender =
            worker.submit(
                () -> {
                  senderThread.set(Thread.currentThread());
                  for (int i = 0; i < 200 && !stop.get(); i++) {
                    client.request("BLOCKED", large);
                  }
                });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (!blocked && System.nanoTime() < deadline) {
          Thread thread = senderThread.get();
          if (thread != null) {
            blocked =
                java.util.Arrays.stream(thread.getStackTrace())
                    .anyMatch(
                        frame ->
                            frame.getClassName().contains("NioSocketImpl")
                                && frame.getMethodName().equals("implWrite"));
          }
          if (!blocked) {
            Thread.sleep(10);
          }
        }
        try {
          assertTrue(blocked, "Test phải thực sự chặn socket write trước khi kiểm close");
          Future<?> closing = worker.submit(client::close);
          assertDoesNotThrow(
              () -> closing.get(2, TimeUnit.SECONDS),
              "Đóng client không được chờ khóa đang bị socket write giữ");
        } finally {
          stop.set(true);
          peer.close();
        }
        sender.get(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void timeoutFailsRequestAndReportsLostConnection() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      CountDownLatch release = new CountDownLatch(1);
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  JsonLines.read(new BufferedInputStream(peer.getInputStream()));
                  release.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client =
          new AuctionConnection("127.0.0.1", listener.getLocalPort(), 100)) {
        CountDownLatch lost = new CountDownLatch(1);
        client.onDisconnect(error -> lost.countDown());
        ExecutionException failure =
            assertThrows(
                ExecutionException.class,
                () -> client.request("NO_REPLY", new JsonObject()).get(3, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof java.util.concurrent.TimeoutException);
        assertTrue(lost.await(2, TimeUnit.SECONDS));
      } finally {
        release.countDown();
      }
      stub.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void brokenEventCallbackDoesNotLoseTheFollowingResponse() throws Exception {
    try (ServerSocket listener = new ServerSocket(0);
        ExecutorService worker = Executors.newSingleThreadExecutor()) {
      Future<?> stub =
          worker.submit(
              () -> {
                try (Socket peer = listener.accept()) {
                  Message request =
                      Message.fromJson(
                          JsonLines.read(new BufferedInputStream(peer.getInputStream())));
                  var out =
                      new BufferedWriter(
                          new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
                  write(out, new Message("AUCTION_UPDATED", null, new JsonObject()));
                  JsonObject result = new JsonObject();
                  result.addProperty("received", true);
                  write(out, Message.result(request.getRequestId(), result));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (AuctionConnection client = new AuctionConnection("127.0.0.1", listener.getLocalPort())) {
        client.onEvent(
            event -> {
              throw new IllegalStateException("test callback");
            });
        assertTrue(
            client
                .request("WITH_EVENT", new JsonObject())
                .get(3, TimeUnit.SECONDS)
                .get("received")
                .getAsBoolean());
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
