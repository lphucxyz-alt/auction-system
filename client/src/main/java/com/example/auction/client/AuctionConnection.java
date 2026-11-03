package com.example.auction.client;

import com.example.auction.common.protocol.JsonLines;
import com.example.auction.common.protocol.Message;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Asynchronous request correlation. All failures complete pending requests and release timers. */
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
  private final BufferedWriter output;
  private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
  private final ScheduledExecutorService timeout =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "request-timeouts");
            t.setDaemon(true);
            return t;
          });
  private volatile Consumer<Message> events = event -> {};
  private volatile Consumer<Throwable> disconnected = error -> {};
  private volatile boolean closed;

  /** Creates an instance with the supplied identity and configuration. */
  public AuctionConnection(String host, int port) throws IOException {
    try {
      socket.connect(new InetSocketAddress(host, port), 5000);
      socket.setTcpNoDelay(true);
      output =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      Thread.ofVirtual().name("auction-receiver").start(this::readLoop);
    } catch (IOException e) {
      timeout.shutdownNow();
      socket.close();
      throw e;
    }
  }

  public void onEvent(Consumer<Message> callback) {
    events = callback;
  }

  public void onDisconnect(Consumer<Throwable> callback) {
    disconnected = callback;
  }

  /** Sends a correlated request and completes it on response, timeout or disconnection. */
  public CompletableFuture<JsonObject> request(String type, JsonObject payload) {
    CompletableFuture<JsonObject> result = new CompletableFuture<>();
    String id = UUID.randomUUID().toString();
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
                  expired.completeExceptionally(
                      new TimeoutException("Server không phản hồi trong 10 giây"));
                }
              },
              10,
              TimeUnit.SECONDS);
      result.whenComplete((value, error) -> timer.cancel(false));
      try {
        String line = new Message(type, id, payload).toJson();
        if (line.getBytes(StandardCharsets.UTF_8).length > JsonLines.MAX_FRAME_BYTES) {
          throw new IOException("Yêu cầu quá lớn");
        }
        output.write(line);
        output.newLine();
        output.flush();
      } catch (IOException e) {
        pending.remove(id);
        result.completeExceptionally(e);
        close();
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
          events.accept(message);
          continue;
        }
        CompletableFuture<JsonObject> result = pending.remove(message.getRequestId());
        if (result == null) {
          continue;
        }
        if (message.getType().equals("ERROR")) {
          result.completeExceptionally(
              new RemoteException(
                  message.getPayload().get("code").getAsString(),
                  message.getPayload().get("message").getAsString()));
        } else {
          result.complete(message.getPayload());
        }
      }
    } catch (IOException | RuntimeException e) {
      reason = e;
    } finally {
      boolean notify = !closed;
      close();
      if (notify) {
        disconnected.accept(reason);
      }
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      socket.close();
    } catch (IOException ignored) {
      // The socket has already been closed.
    }
    pending.values().forEach(f -> f.completeExceptionally(new IOException("Mất kết nối server")));
    pending.clear();
    timeout.shutdownNow();
  }
}
