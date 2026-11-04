package com.example.auction.client;

import com.example.auction.client.protocol.JsonLines;
import com.example.auction.client.protocol.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Ghép phản hồi theo requestId; đóng kết nối phải giải phóng mọi yêu cầu đang chờ. */
public final class AuctionConnection implements AutoCloseable {
  /** Typed server rejection returned to an asynchronous request. */
  public static final class RemoteException extends RuntimeException {
    private final String code;

    public RemoteException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  private final Socket socket = new Socket();
  private final Object writeLock = new Object();
  private final BufferedWriter output;
  private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
  private final ScheduledThreadPoolExecutor timeout =
      new ScheduledThreadPoolExecutor(
          1,
          task -> {
            Thread thread = new Thread(task, "request-timeouts");
            thread.setDaemon(true);
            return thread;
          });
  private final long requestTimeoutMillis;
  private volatile Consumer<Message> events = event -> {};
  private Consumer<Throwable> disconnected;
  private Throwable unexpectedFailure;
  private boolean disconnectNotified;
  private volatile boolean closed;

  /** Kết nối server và mở luồng nhận; timeout mặc định là 10 giây. */
  public AuctionConnection(String host, int port) throws IOException {
    this(host, port, 10_000);
  }

  AuctionConnection(String host, int port, long requestTimeoutMillis) throws IOException {
    if (requestTimeoutMillis <= 0) {
      throw new IllegalArgumentException("Timeout phải > 0");
    }
    this.requestTimeoutMillis = requestTimeoutMillis;
    timeout.setRemoveOnCancelPolicy(true);
    try {
      socket.connect(new InetSocketAddress(host, port), 5000);
      socket.setTcpNoDelay(true);
      output =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      Thread receiver = new Thread(this::readLoop, "auction-receiver");
      receiver.setDaemon(true);
      receiver.start();
    } catch (IOException | RuntimeException e) {
      timeout.shutdownNow();
      socket.close();
      throw e;
    }
  }

  public void onEvent(Consumer<Message> callback) {
    events = callback;
  }

  /** Báo lại cả lỗi đã xảy ra trước khi màn hình kịp gắn callback. */
  public void onDisconnect(Consumer<Throwable> callback) {
    synchronized (this) {
      disconnected = callback;
    }
    notifyDisconnect();
  }

  /** Sends a correlated request and completes it on response, timeout or disconnection. */
  public CompletableFuture<JsonObject> request(String type, JsonObject payload) {
    String id = UUID.randomUUID().toString();
    String line = new Message(type, id, payload).toJson();
    if (line.getBytes(StandardCharsets.UTF_8).length > JsonLines.MAX_FRAME_BYTES) {
      return CompletableFuture.failedFuture(new IOException("Yêu cầu quá lớn"));
    }
    CompletableFuture<JsonObject> result = new CompletableFuture<>();
    synchronized (this) {
      if (closed) {
        return CompletableFuture.failedFuture(new IOException("Mất kết nối server"));
      }
      pending.put(id, result);
      ScheduledFuture<?> timer =
          timeout.schedule(
              () -> {
                CompletableFuture<JsonObject> expired = pending.remove(id);
                if (expired != null) {
                  TimeoutException failure =
                      new TimeoutException("Server không phản hồi đúng hạn; cần kết nối lại");
                  expired.completeExceptionally(failure);
                  terminate(failure, true);
                }
              },
              requestTimeoutMillis,
              TimeUnit.MILLISECONDS);
      result.whenComplete(
          (value, error) -> {
            timer.cancel(false);
            pending.remove(id, result);
          });
    }
    // Chỉ khóa các writer với nhau. close() phải đóng được socket dù write đang bị chặn.
    synchronized (writeLock) {
      if (!closed) {
        try {
          output.write(line);
          output.newLine();
          output.flush();
        } catch (IOException e) {
          terminate(e, true);
        }
      }
    }
    return result;
  }

  private void readLoop() {
    Throwable reason = new IOException("Kết nối đã đóng");
    try (InputStream input = new BufferedInputStream(socket.getInputStream())) {
      String line;
      while ((line = JsonLines.read(input, 4 * 1024 * 1024)) != null) {
        Message message = Message.fromJson(line);
        if (message.getRequestId() == null) {
          try {
            events.accept(message);
          } catch (RuntimeException e) {
            System.err.println("Callback sự kiện lỗi: " + e.getClass().getSimpleName());
          }
          continue;
        }
        CompletableFuture<JsonObject> result = pending.get(message.getRequestId());
        if (result == null) {
          continue;
        }
        // Kiểm phản hồi trước khi xóa khỏi pending; phản hồi hỏng vẫn phải hoàn tất future.
        RemoteException rejection = null;
        if (message.getType().equals("ERROR")) {
          rejection =
              new RemoteException(
                  errorText(message.getPayload(), "code"),
                  errorText(message.getPayload(), "message"));
        } else if (!message.getType().equals("RESULT")) {
          throw new IOException("Loại phản hồi không hợp lệ");
        }
        if (pending.remove(message.getRequestId(), result)) {
          if (rejection != null) {
            result.completeExceptionally(rejection);
          } else {
            result.complete(message.getPayload());
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      reason = e;
    } finally {
      terminate(reason, true);
    }
  }

  private static String errorText(JsonObject payload, String name) throws IOException {
    JsonElement value = payload.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IOException("Phản hồi ERROR thiếu trường hợp lệ: " + name);
    }
    return value.getAsString();
  }

  private void terminate(Throwable reason, boolean unexpected) {
    List<CompletableFuture<JsonObject>> waiting;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      if (unexpected) {
        unexpectedFailure = reason;
      }
      waiting = new ArrayList<>(pending.values());
      pending.clear();
    }
    try {
      socket.close();
    } catch (IOException ignored) {
      /* Socket đã đóng. */
    }
    timeout.shutdownNow();
    waiting.forEach(future -> future.completeExceptionally(reason));
    notifyDisconnect();
  }

  private void notifyDisconnect() {
    Consumer<Throwable> callback;
    Throwable reason;
    synchronized (this) {
      if (unexpectedFailure == null || disconnected == null || disconnectNotified) {
        return;
      }
      disconnectNotified = true;
      callback = disconnected;
      reason = unexpectedFailure;
    }
    try {
      callback.accept(reason);
    } catch (RuntimeException e) {
      System.err.println("Callback mất kết nối lỗi: " + e.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    terminate(new IOException("Kết nối đã đóng"), false);
  }
}
