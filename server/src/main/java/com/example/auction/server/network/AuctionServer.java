package com.example.auction.server.network;

import com.example.auction.common.exception.AuctionClosedException;
import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.AuthenticationException;
import com.example.auction.common.exception.InvalidBidException;
import com.example.auction.common.model.Auction;
import com.example.auction.common.model.User;
import com.example.auction.server.ApiException;
import com.example.auction.server.AuctionApplication;
import com.example.auction.server.protocol.JsonLines;
import com.example.auction.server.protocol.Message;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/** Socket controller. Slow consumers have bounded queues and cannot hold the model lock. */
public final class AuctionServer implements AutoCloseable {
  private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
  private static final int MAX_PENDING_OUTPUT_BYTES = 8 * 1024 * 1024;
  private final AuctionApplication app;
  private final ServerSocket server;
  private final ExecutorService workers = Executors.newCachedThreadPool();
  private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
  private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;
  private final BiConsumer<String, Auction> observer;

  /** Creates an instance with the supplied identity and configuration. */
  public AuctionServer(AuctionApplication app, String host, int port) throws IOException {
    this.app = app;
    server = new ServerSocket();
    server.setReuseAddress(true);
    server.bind(new InetSocketAddress(host, port));
    observer =
        (type, auction) -> {
          if (closed) {
            return;
          }
          JsonObject payload = app.envelope(auction);
          for (Session session : sessions) {
            if (type.equals("AUCTION_DELETED")) {
              session.subscriptions.remove(auction.getId());
            }
            if (session.user == null) {
              continue;
            }
            session.send(
                new Message(
                    type.equals("AUCTION_DELETED") ? type : "AUCTION_UPDATED", null, payload));
            if (!type.equals("AUCTION_UPDATED")
                && !type.equals("AUCTION_DELETED")
                && session.subscriptions.contains(auction.getId())) {
              session.send(new Message(type, null, payload));
            }
          }
        };
    app.addObserver(observer);
  }

  public int getPort() {
    return server.getLocalPort();
  }

  /** Starts the server accept loop and scheduled auction clock. */
  public void start() {
    clock.scheduleAtFixedRate(
        () -> {
          try {
            app.tick();
          } catch (RuntimeException e) {
            System.err.println("Clock: " + e.getMessage());
          }
        },
        0,
        100,
        TimeUnit.MILLISECONDS);
    workers.submit(
        () -> {
          while (!closed) {
            try {
              Socket socket = server.accept();
              if (sessions.size() >= 64) {
                socket.close();
                continue;
              }
              socket.setTcpNoDelay(true);
              Session session = new Session(socket);
              sessions.add(session);
              workers.submit(session::run);
            } catch (IOException e) {
              if (!closed) {
                System.err.println("Accept: " + e.getMessage());
              }
            }
          }
        });
  }

  private final class Session {
    private final Socket socket;
    private final BlockingQueue<byte[]> output = new ArrayBlockingQueue<>(256);
    private final AtomicLong pendingOutputBytes = new AtomicLong();
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private volatile User user;
    private volatile boolean ended;

    Session(Socket socket) {
      this.socket = socket;
    }

    void send(Message message) {
      if (ended) {
        return;
      }
      byte[] frame = (message.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
      if (frame.length > MAX_RESPONSE_BYTES + 1) {
        disconnect();
        return;
      }
      long retained = pendingOutputBytes.addAndGet(frame.length);
      if (retained > MAX_PENDING_OUTPUT_BYTES || ended || !output.offer(frame)) {
        pendingOutputBytes.addAndGet(-frame.length);
        disconnect();
      }
    }

    void disconnect() {
      ended = true;
      try {
        socket.close();
      } catch (IOException ignored) {
        // The socket has already been closed.
      }
    }

    void run() {
      Future<?> writer =
          workers.submit(
              () -> {
                try {
                  OutputStream out = new BufferedOutputStream(socket.getOutputStream());
                  while (!ended) {
                    byte[] frame = output.take();
                    try {
                      out.write(frame);
                      out.flush();
                    } finally {
                      pendingOutputBytes.addAndGet(-frame.length);
                    }
                  }
                } catch (IOException | InterruptedException e) {
                  disconnect();
                }
              });
      try (InputStream input = new BufferedInputStream(socket.getInputStream())) {
        String line;
        while (!ended && (line = JsonLines.read(input)) != null) {
          Message request = null;
          try {
            request = Message.fromJson(line);
            if (request.getRequestId() == null || request.getRequestId().length() > 100) {
              throw new ApiException("BAD_REQUEST", "Cần requestId dài tối đa 100 ký tự");
            }
            JsonObject result = dispatch(request);
            send(Message.result(request.getRequestId(), result));
          } catch (ApiException e) {
            error(request, e.getCode(), e.getMessage());
          } catch (AuthenticationException e) {
            error(request, "AUTH_FAILED", e.getMessage());
          } catch (InvalidBidException e) {
            error(request, "INVALID_BID", e.getMessage());
          } catch (AuctionClosedException e) {
            error(request, "AUCTION_CLOSED", e.getMessage());
          } catch (AuctionException e) {
            error(request, "CONFLICT", e.getMessage());
          } catch (IllegalArgumentException | IllegalStateException e) {
            error(request, "BAD_REQUEST", "Dữ liệu không hợp lệ: " + e.getMessage());
          } catch (RuntimeException e) {
            error(request, "INTERNAL", "Lỗi xử lý yêu cầu");
          }
        }
      } catch (IOException ignored) {
        // EOF, oversized frames and disconnects release every subscription and writer.
      } finally {
        disconnect();
        subscriptions.clear();
        sessions.remove(this);
        writer.cancel(true);
      }
    }

    JsonObject dispatch(Message request) {
      String type = request.getType();
      JsonObject p = request.getPayload();
      return switch (type) {
        case "REGISTER" -> {
          JsonObject out = new JsonObject();
          out.add("user", AuctionApplication.userView(app.register(p)));
          yield out;
        }
        case "LOGIN" -> {
          if (user != null) {
            throw new ApiException("CONFLICT", "Đã đăng nhập; hãy đăng xuất trước");
          }
          user =
              app.login(
                  AuctionApplication.text(p, "username"), AuctionApplication.text(p, "password"));
          JsonObject out = new JsonObject();
          out.add("user", AuctionApplication.userView(user));
          yield out;
        }
        case "LOGOUT" -> {
          user = null;
          subscriptions.clear();
          yield new JsonObject();
        }
        case "SUBSCRIBE" -> {
          synchronized (app) {
            JsonObject out = app.execute(user, type, p);
            if (subscriptions.size() >= 100
                && !subscriptions.contains(AuctionApplication.text(p, "auctionId"))) {
              throw new ApiException("CONFLICT", "Tối đa 100 subscriptions");
            }
            subscriptions.add(AuctionApplication.text(p, "auctionId"));
            yield out;
          }
        }
        case "UNSUBSCRIBE" -> {
          subscriptions.remove(AuctionApplication.text(p, "auctionId"));
          yield new JsonObject();
        }
        default -> app.execute(user, type, p);
      };
    }

    void error(Message request, String code, String text) {
      send(Message.error(request == null ? null : request.getRequestId(), code, text));
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    app.removeObserver(observer);
    server.close();
    clock.shutdownNow();
    sessions.forEach(Session::disconnect);
    workers.shutdownNow();
    try {
      clock.awaitTermination(5, TimeUnit.SECONDS);
      workers.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
