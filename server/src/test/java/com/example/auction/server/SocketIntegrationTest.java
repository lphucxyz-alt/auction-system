package com.example.auction.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.auction.server.dao.FileStore;
import com.example.auction.server.network.AuctionServer;
import com.example.auction.server.protocol.JsonLines;
import com.example.auction.server.protocol.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocketIntegrationTest {
  @TempDir Path data;
  private FileStore store;
  private AuctionApplication app;
  private AuctionServer server;
  private final List<Peer> peers = new ArrayList<>();

  @BeforeEach
  void setup() throws Exception {
    store = new FileStore(data);
    app = new AuctionApplication(store);
    server = new AuctionServer(app, "127.0.0.1", 0);
    server.start();
  }

  @AfterEach
  void cleanup() throws Exception {
    for (Peer peer : peers) {
      peer.close();
    }
    server.close();
    store.close();
  }

  private Peer peer() throws Exception {
    Peer p = new Peer(server.getPort());
    peers.add(p);
    return p;
  }

  private Peer account(String name, String role) throws Exception {
    Peer p = peer();
    assertEquals(
        "RESULT",
        p.call("REGISTER", object("username", name, "password", "Secret123!", "role", role))
            .getType());
    p.ok("LOGIN", object("username", name, "password", "Secret123!"));
    return p;
  }

  private JsonObject draft(long duration, boolean anti) {
    JsonObject p =
        object(
            "name",
            "Camera",
            "description",
            "Near mint",
            "category",
            "ELECTRONICS",
            "extra1",
            "Fuji");
    p.addProperty("extra2", 12);
    p.addProperty("startingPrice", 100);
    p.addProperty("startDelaySeconds", 3600);
    p.addProperty("durationSeconds", duration);
    p.addProperty("antiSniping", anti);
    return p;
  }

  private String create(Peer seller, long duration, boolean anti) throws Exception {
    return seller
        .ok("CREATE_AUCTION", draft(duration, anti))
        .getAsJsonObject("auction")
        .get("id")
        .getAsString();
  }

  private static JsonObject id(String id) {
    return object("auctionId", id);
  }

  private static JsonObject object(String... strings) {
    JsonObject p = new JsonObject();
    for (int i = 0; i < strings.length; i += 2) {
      p.addProperty(strings[i], strings[i + 1]);
    }
    return p;
  }

  private void code(Peer peer, String type, JsonObject p, String code) throws Exception {
    Message response = peer.call(type, p);
    assertEquals("ERROR", response.getType(), response.toJson());
    assertEquals(code, response.getPayload().get("code").getAsString());
  }

  @Test
  void completeFlowPushWinnerPaymentAndRestart() throws Exception {
    final Peer seller = account("seller", "SELLER");
    final Peer one = account("buyer1", "BIDDER");
    final Peer two = account("buyer2", "BIDDER");
    String id = create(seller, 1, false);
    seller.ok("START_AUCTION", id(id));
    one.ok("SUBSCRIBE", id(id));
    two.ok("SUBSCRIBE", id(id));
    JsonObject p = id(id);
    p.addProperty("amount", 150);
    one.ok("PLACE_BID", p);
    assertTrue(
        two.events.stream()
            .noneMatch(
                e -> e.getType().equals("NEW_BID"))); // read pump below receives queued event
    two.ok("GET_AUCTION", id(id));
    assertTrue(two.events.stream().anyMatch(e -> e.getType().equals("NEW_BID")));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    JsonObject auction;
    do {
      Thread.sleep(80);
      auction = one.ok("GET_AUCTION", id(id)).getAsJsonObject("auction");
    } while (!auction.get("status").getAsString().equals("FINISHED")
        && System.nanoTime() < deadline);
    assertEquals("FINISHED", auction.get("status").getAsString());
    assertEquals("buyer1", auction.get("leader").getAsString());
    assertTrue(one.events.stream().anyMatch(e -> e.getType().equals("AUCTION_FINISHED")));
    code(two, "PAY_AUCTION", id(id), "FORBIDDEN");
    one.ok("PAY_AUCTION", id(id));
    server.close();
    store.close();
    store = new FileStore(data);
    app = new AuctionApplication(store);
    server = new AuctionServer(app, "127.0.0.1", 0);
    server.start();
    Peer restored = peer();
    restored.ok("LOGIN", object("username", "buyer1", "password", "Secret123!"));
    JsonObject saved = restored.ok("GET_AUCTION", id(id)).getAsJsonObject("auction");
    assertEquals("PAID", saved.get("status").getAsString());
    assertEquals(150, saved.get("currentPrice").getAsLong());
    assertEquals(1, saved.getAsJsonArray("history").size());
  }

  @Test
  void rolesOwnershipAndValidationAreEnforcedOnServer() throws Exception {
    Peer guest = peer();
    code(guest, "LIST_AUCTIONS", new JsonObject(), "NOT_LOGGED_IN");
    code(
        guest,
        "REGISTER",
        object("username", "evil", "password", "Secret123!", "role", "ADMIN"),
        "FORBIDDEN");
    code(guest, "LOGIN", object("username", "nobody", "password", "wrong"), "AUTH_FAILED");
    final Peer seller = account("seller", "SELLER");
    final Peer other = account("other", "SELLER");
    final Peer buyer = account("buyer", "BIDDER");
    String id = create(seller, 60, false);
    code(buyer, "CREATE_AUCTION", draft(10, false), "FORBIDDEN");
    code(other, "START_AUCTION", id(id), "FORBIDDEN");
    code(other, "DELETE_AUCTION", id(id), "FORBIDDEN");
    code(buyer, "LIST_USERS", new JsonObject(), "FORBIDDEN");
    code(buyer, "GET_AUCTION", id("missing"), "NOT_FOUND");
    JsonObject bid = id(id);
    bid.addProperty("amount", 101);
    code(buyer, "PLACE_BID", bid, "AUCTION_CLOSED");
    seller.ok("START_AUCTION", id(id));
    code(seller, "PLACE_BID", bid, "FORBIDDEN");
    bid.addProperty("amount", 100);
    code(buyer, "PLACE_BID", bid, "INVALID_BID");
    bid.addProperty("amount", 101.5);
    code(buyer, "PLACE_BID", bid, "BAD_REQUEST");
    bid.addProperty("amount", "102");
    code(buyer, "PLACE_BID", bid, "BAD_REQUEST");
    bid.addProperty("amount", 200);
    buyer.ok("PLACE_BID", bid);
    JsonObject update = draft(60, false);
    update.addProperty("auctionId", id);
    code(seller, "UPDATE_AUCTION", update, "CONFLICT");
    code(seller, "DELETE_AUCTION", id(id), "CONFLICT");
    seller.ok("CANCEL_AUCTION", id(id));
    code(buyer, "PLACE_BID", bid, "AUCTION_CLOSED");
    code(buyer, "UNKNOWN", id(id), "BAD_REQUEST");
    buyer.ok("LOGOUT", new JsonObject());
    code(buyer, "SUBSCRIBE", id(id), "NOT_LOGGED_IN");
  }

  @Test
  void crudAdminAndSecretsNeverLeak() throws Exception {
    app.bootstrap("admin", "StrongPass123!");
    Peer admin = peer();
    admin.ok("LOGIN", object("username", "admin", "password", "StrongPass123!"));
    String users = admin.ok("LIST_USERS", new JsonObject()).toString();
    assertFalse(users.contains("password"));
    assertFalse(users.contains("StrongPass"));
    final Peer seller = account("seller", "SELLER");
    String id = create(seller, 30, true);
    JsonObject update = draft(15, false);
    update.addProperty("auctionId", id);
    update.addProperty("category", "ART");
    update.addProperty("name", "Painting");
    assertEquals(
        "Painting",
        seller.ok("UPDATE_AUCTION", update).getAsJsonObject("auction").get("name").getAsString());
    admin.ok("DELETE_AUCTION", id(id));
    code(seller, "GET_AUCTION", id(id), "NOT_FOUND");
    assertEquals(0, seller.ok("LIST_AUCTIONS", new JsonObject()).getAsJsonArray("auctions").size());
  }

  @Test
  void autoBiddingAntiSnipingAndRestoredAutoRules() throws Exception {
    final Peer seller = account("seller", "SELLER");
    final Peer one = account("buyer1", "BIDDER");
    final Peer two = account("buyer2", "BIDDER");
    String id = create(seller, 5, true);
    seller.ok("START_AUCTION", id(id));
    JsonObject config = id(id);
    config.addProperty("maxBid", 1000);
    config.addProperty("increment", 10);
    JsonObject first = one.ok("AUTO_BID", config).getAsJsonObject("auction");
    assertTrue(
        java.time.Duration.between(
                    java.time.Instant.now(),
                    java.time.Instant.parse(first.get("endTime").getAsString()))
                .getSeconds()
            >= 28);
    assertFalse(first.toString().contains("maxBid"));
    server.close();
    store.close();
    store = new FileStore(data);
    app = new AuctionApplication(store);
    server = new AuctionServer(app, "127.0.0.1", 0);
    server.start();
    Peer competitor = peer();
    competitor.ok("LOGIN", object("username", "buyer2", "password", "Secret123!"));
    JsonObject bid = id(id);
    bid.addProperty("amount", 200);
    JsonObject response = competitor.ok("PLACE_BID", bid).getAsJsonObject("auction");
    assertEquals("buyer1", response.get("leader").getAsString());
    assertEquals(210, response.get("currentPrice").getAsLong());
    Peer owner = peer();
    owner.ok("LOGIN", object("username", "buyer1", "password", "Secret123!"));
    owner.ok("REMOVE_AUTO_BID", id(id));
    bid.addProperty("amount", 250);
    assertEquals(
        "buyer2",
        competitor.ok("PLACE_BID", bid).getAsJsonObject("auction").get("leader").getAsString());
  }

  @Test
  void simultaneousRealSocketBidsNeverRollback() throws Exception {
    final Peer seller = account("seller", "SELLER");
    String id = create(seller, 30, false);
    seller.ok("START_AUCTION", id(id));
    List<Peer> bidders = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      bidders.add(account("buyer" + i, "BIDDER"));
    }
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Message>> futures = new ArrayList<>();
      for (int i = 0; i < bidders.size(); i++) {
        final int index = i;
        futures.add(
            pool.submit(
                () -> {
                  go.await();
                  JsonObject bid = id(id);
                  bid.addProperty("amount", 200 + index);
                  return bidders.get(index).call("PLACE_BID", bid);
                }));
      }
      go.countDown();
      for (Future<Message> future : futures) {
        Message m = future.get(10, TimeUnit.SECONDS);
        assertTrue(
            m.getType().equals("RESULT")
                || m.getPayload().get("code").getAsString().equals("INVALID_BID"));
      }
    }
    JsonObject a = seller.ok("GET_AUCTION", id(id)).getAsJsonObject("auction");
    assertEquals(211, a.get("currentPrice").getAsLong());
    assertEquals("buyer11", a.get("leader").getAsString());
    long last = 100;
    for (JsonElement e : a.getAsJsonArray("history")) {
      long price = e.getAsJsonObject().get("amount").getAsLong();
      assertTrue(price > last);
      last = price;
    }
  }

  @Test
  void malformedJsonIsRecoverableAndOversizedInputClosesConnection() throws Exception {
    Peer p = peer();
    p.out.write("{no-json}\n");
    p.out.flush();
    assertEquals("ERROR", p.read().getType());
    p.out.write("{\"type\":\"LOGIN\",\"requestId\":\"test\"}\n");
    p.out.flush();
    assertEquals("BAD_REQUEST", p.read().getPayload().get("code").getAsString());
    p.out.write("x".repeat(65_538) + "\n");
    p.out.flush();
    assertThrows(IOException.class, p::read);
  }

  @Test
  void scheduledStartAndCloseWithoutRequests() throws Exception {
    final Peer seller = account("seller", "SELLER");
    JsonObject p = draft(1, false);
    p.addProperty("startDelaySeconds", 0);
    String id = seller.ok("CREATE_AUCTION", p).getAsJsonObject("auction").get("id").getAsString();
    Thread.sleep(1400);
    assertEquals(
        "FINISHED",
        seller.ok("GET_AUCTION", id(id)).getAsJsonObject("auction").get("status").getAsString());
  }

  @Test
  void storeLocksAndCorruptionDoesNotOverwriteData() throws Exception {
    assertThrows(IOException.class, () -> new FileStore(data));
    server.close();
    store.close();
    byte[] broken = {1, 2, 3};
    Files.write(data.resolve("auction-state.bin"), broken);
    store = new FileStore(data);
    assertThrows(IOException.class, () -> new AuctionApplication(store));
    assertArrayEquals(broken, Files.readAllBytes(data.resolve("auction-state.bin")));
  }

  @Test
  void completeHistoryIsPaginatedAndSnapshotIsBounded() throws Exception {
    final Peer seller = account("seller", "SELLER");
    Peer buyer = account("buyer", "BIDDER");
    String auctionId = create(seller, 120, false);
    seller.ok("START_AUCTION", id(auctionId));
    for (int i = 1; i <= 105; i++) {
      JsonObject bid = id(auctionId);
      bid.addProperty("amount", 100 + i);
      buyer.ok("PLACE_BID", bid);
    }
    JsonObject snapshot = buyer.ok("GET_AUCTION", id(auctionId)).getAsJsonObject("auction");
    assertEquals(105, snapshot.get("historyCount").getAsInt());
    assertEquals(100, snapshot.getAsJsonArray("history").size());
    JsonObject first = buyer.ok("GET_HISTORY", id(auctionId));
    assertEquals(100, first.getAsJsonArray("history").size());
    assertEquals(
        101, first.getAsJsonArray("history").get(0).getAsJsonObject().get("amount").getAsInt());
    JsonObject next = id(auctionId);
    next.addProperty("offset", 100);
    JsonObject last = buyer.ok("GET_HISTORY", next);
    assertEquals(5, last.getAsJsonArray("history").size());
    assertEquals(-1, last.get("nextOffset").getAsInt());
    next.addProperty("offset", Integer.MAX_VALUE);
    assertEquals(0, buyer.ok("GET_HISTORY", next).getAsJsonArray("history").size());
    JsonObject page = new JsonObject();
    page.addProperty("offset", Integer.MAX_VALUE);
    assertEquals(0, buyer.ok("LIST_AUCTIONS", page).getAsJsonArray("auctions").size());
  }

  @Test
  void writeFailureFailsClosedUntilRestart() throws Exception {
    final Peer seller = account("seller", "SELLER");
    Path snapshot = data.resolve("auction-state.bin");
    final byte[] saved = Files.readAllBytes(snapshot);
    Files.delete(snapshot);
    Files.createDirectory(snapshot);
    Files.writeString(snapshot.resolve("block-move"), "simulated IO fault");
    code(seller, "CREATE_AUCTION", draft(60, false), "STORAGE_ERROR");
    code(seller, "LIST_AUCTIONS", new JsonObject(), "STORAGE_ERROR");
    Files.delete(snapshot.resolve("block-move"));
    Files.delete(snapshot);
    Files.write(snapshot, saved);
    server.close();
    store.close();
    store = new FileStore(data);
    app = new AuctionApplication(store);
    server = new AuctionServer(app, "127.0.0.1", 0);
    server.start();
    Peer restored = peer();
    restored.ok("LOGIN", object("username", "seller", "password", "Secret123!"));
    assertEquals(
        0, restored.ok("LIST_AUCTIONS", new JsonObject()).getAsJsonArray("auctions").size());
  }

  @Test
  void brokenObserverDoesNotTurnCommittedCommandIntoAnError() throws Exception {
    Peer seller = account("seller", "SELLER");
    app.addObserver(
        (type, auction) -> {
          throw new IllegalStateException("observer failed");
        });
    String auctionId = create(seller, 60, false);
    assertEquals(
        auctionId,
        seller.ok("GET_AUCTION", id(auctionId)).getAsJsonObject("auction").get("id").getAsString());
  }

  @Test
  void cursorPaginationDoesNotSkipLotsWhenEarlierLotIsDeleted() throws Exception {
    Peer seller = account("seller", "SELLER");
    for (int i = 0; i < 55; i++) {
      create(seller, 60, false);
    }
    JsonObject first = seller.ok("LIST_AUCTIONS", new JsonObject());
    var rows = first.getAsJsonArray("auctions");
    assertEquals(50, rows.size());
    String cursor = rows.get(49).getAsJsonObject().get("id").getAsString();
    seller.ok("DELETE_AUCTION", id(rows.get(0).getAsJsonObject().get("id").getAsString()));
    JsonObject page = new JsonObject();
    page.addProperty("afterId", cursor);
    var remaining = seller.ok("LIST_AUCTIONS", page).getAsJsonArray("auctions");
    assertEquals(
        5, remaining.size(), "Xóa phần tử trang trước không được làm mất phần tử trang sau");
    for (JsonElement a : remaining) {
      String found = a.getAsJsonObject().get("id").getAsString();
      assertTrue(
          rows.asList().stream()
              .noneMatch(b -> b.getAsJsonObject().get("id").getAsString().equals(found)));
    }
  }

  @Test
  void slowReaderWithLargeResponsesIsDroppedBeforeTwentyRepliesQueueUp() throws Exception {
    Peer seller = account("seller", "SELLER");
    for (int i = 0; i < 50; i++) {
      JsonObject data = draft(60, false);
      data.addProperty("description", "漢".repeat(4000));
      data.addProperty("extra1", "漢".repeat(4000));
      seller.ok("CREATE_AUCTION", data);
    }
    try (Socket slow = new Socket()) {
      slow.setReceiveBufferSize(1024);
      slow.connect(new java.net.InetSocketAddress("127.0.0.1", server.getPort()));
      slow.setSoTimeout(5000);
      var output =
          new BufferedWriter(
              new OutputStreamWriter(slow.getOutputStream(), StandardCharsets.UTF_8));
      final var input = new BufferedInputStream(slow.getInputStream());
      output.write(
          new Message("LOGIN", "login", object("username", "seller", "password", "Secret123!"))
              .toJson());
      output.newLine();
      output.flush();
      assertEquals("RESULT", Message.fromJson(JsonLines.read(input)).getType());
      for (int i = 0; i < 20; i++) {
        output.write(new Message("LIST_AUCTIONS", "bulk-" + i, new JsonObject()).toJson());
        output.newLine();
      }
      output.flush();
      int complete = 0;
      try {
        String line;
        while ((line = JsonLines.read(input, 4 * 1024 * 1024)) != null) {
          complete++;
          assertTrue(complete < 20, "Client không đọc phải bị ngắt khi hết ngân sách byte");
        }
      } catch (java.net.SocketTimeoutException timeout) {
        throw new AssertionError("Server phải chủ động ngắt client chậm", timeout);
      } catch (IOException expected) {
        // EOF/reset hoặc frame dở dang là kết quả của việc đóng kết nối chậm.
      }
    }
    assertEquals(50, seller.ok("LIST_AUCTIONS", new JsonObject()).get("total").getAsInt());
  }

  static final class Peer implements AutoCloseable {
    final Socket socket;
    final BufferedWriter out;
    final InputStream in;
    final List<Message> events = new ArrayList<>();

    Peer(int port) throws Exception {
      socket = new Socket("127.0.0.1", port);
      socket.setSoTimeout(5000);
      in = new BufferedInputStream(socket.getInputStream());
      out =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    Message read() throws IOException {
      String line = JsonLines.read(in, 4 * 1024 * 1024);
      if (line == null) {
        throw new EOFException();
      }
      return Message.fromJson(line);
    }

    Message call(String type, JsonObject payload) throws Exception {
      String id = UUID.randomUUID().toString();
      out.write(new Message(type, id, payload).toJson());
      out.newLine();
      out.flush();
      while (true) {
        Message response = read();
        if (id.equals(response.getRequestId())) {
          return response;
        }
        events.add(response);
      }
    }

    JsonObject ok(String type, JsonObject payload) throws Exception {
      Message response = call(type, payload);
      assertEquals("RESULT", response.getType(), response.toJson());
      return response.getPayload();
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
