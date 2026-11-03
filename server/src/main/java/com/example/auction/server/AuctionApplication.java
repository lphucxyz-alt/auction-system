package com.example.auction.server;

import com.example.auction.common.model.Art;
import com.example.auction.common.model.Auction;
import com.example.auction.common.model.AuctionStatus;
import com.example.auction.common.model.BidTransaction;
import com.example.auction.common.model.Electronics;
import com.example.auction.common.model.Item;
import com.example.auction.common.model.ItemFactory;
import com.example.auction.common.model.ItemType;
import com.example.auction.common.model.User;
import com.example.auction.common.model.Vehicle;
import com.example.auction.server.dao.FileStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Model/service facade: a command, durable snapshot and its event are ordered together. */
public final class AuctionApplication {
  private final FileStore store;
  private final UserService users = new UserService();
  private final Map<String, Auction> auctions = new LinkedHashMap<>();
  private final List<BiConsumer<String, Auction>> observers = new CopyOnWriteArrayList<>();
  private boolean storageFailed;
  private final Set<String> observedBids = new HashSet<>();

  /** Creates an instance with the supplied identity and configuration. */
  public AuctionApplication(FileStore store) throws IOException {
    this.store = store;
    FileStore.State state = store.load();
    users.restore(state.users());
    for (Auction auction : state.auctions()) {
      auctions.put(auction.getId(), auction);
      attach(auction);
    }
  }

  public void addObserver(BiConsumer<String, Auction> observer) {
    observers.add(observer);
  }

  private void attach(Auction auction) {
    // Domain Observer collects committed bids. Network writes run on each session's own queue.
    auction.addListener((id, bid) -> observedBids.add(id));
  }

  public synchronized User login(String username, String password) {
    return users.login(username, password);
  }

  /** Validates and registers a unique account, hashing its password with a random salt. */
  public synchronized User register(JsonObject payload) {
    writable();
    String role = text(payload, "role");
    if (!Set.of("BIDDER", "SELLER").contains(role)) {
      fail("FORBIDDEN", "Chỉ đăng ký BIDDER/SELLER");
    }
    User user = users.register(text(payload, "username"), text(payload, "password"), role);
    persist();
    return user;
  }

  /** Creates an administrator only when no administrator already exists. */
  public synchronized void bootstrap(String username, String password) {
    if (users.getUsers().stream().noneMatch(u -> u.getRole().equals("ADMIN"))) {
      writable();
      users.register(username, password, "ADMIN");
      persist();
    }
  }

  /** Seeds explicitly requested demo accounts and lots in an empty store only. */
  public synchronized void seedDemo() {
    if (!users.getUsers().isEmpty()) {
      return;
    }
    users.register("admin", "DemoAdmin123!", "ADMIN");
    User seller = users.register("seller", "DemoSeller123!", "SELLER");
    users.register("bidder1", "DemoBidder123!", "BIDDER");
    users.register("bidder2", "DemoBidder123!", "BIDDER");
    for (ItemType type : ItemType.values()) {
      Item item =
          ItemFactory.create(
              type,
              switch (type) {
                case ELECTRONICS -> "Máy ảnh Fujifilm X100";
                case ART -> "Tranh sơn dầu • Nắng Hà Nội";
                case VEHICLE -> "Vespa Primavera 2023";
              },
              "Sản phẩm demo — kiểm tra mô tả và trạng thái trước khi đặt giá.",
              switch (type) {
                case ELECTRONICS -> 8_000_000;
                case ART -> 2_000_000;
                case VEHICLE -> 35_000_000;
              },
              "Demo",
              switch (type) {
                case ELECTRONICS -> 12;
                case ART -> 2023;
                case VEHICLE -> 1200;
              });
      Auction a =
          new Auction(
              item,
              seller.getId(),
              Instant.now().plusSeconds(300),
              Instant.now().plusSeconds(900),
              true);
      auctions.put(a.getId(), a);
      attach(a);
    }
    persist();
  }

  /** Projects public account fields; credentials and password hashes are excluded. */
  public static JsonObject userView(User user) {
    JsonObject view = new JsonObject();
    view.addProperty("id", user.getId());
    view.addProperty("username", user.getUsername());
    view.addProperty("role", user.getRole());
    return view;
  }

  /** Authorizes a command and orders its mutation, durable snapshot and event. */
  public synchronized JsonObject execute(User user, String type, JsonObject p) {
    if (user == null) {
      fail("NOT_LOGGED_IN", "Vui lòng đăng nhập");
    }
    writable();
    tick();
    if (type.equals("LIST_AUCTIONS")) {
      int offset = p.has("offset") ? (int) number(p, "offset", 0, Integer.MAX_VALUE) : 0;
      List<Auction> all = new ArrayList<>(auctions.values());
      int end = (int) Math.min(all.size(), (long) offset + 50);
      JsonArray list = new JsonArray();
      for (int i = offset; i < end; i++) {
        list.add(view(all.get(i)));
      }
      JsonObject out = new JsonObject();
      out.add("auctions", list);
      out.addProperty("nextOffset", end < all.size() ? end : -1);
      out.addProperty("total", all.size());
      return out;
    }
    if (type.equals("LIST_USERS")) {
      role(user, "ADMIN");
      JsonArray list = new JsonArray();
      users.getUsers().forEach(u -> list.add(userView(u)));
      JsonObject out = new JsonObject();
      out.add("users", list);
      return out;
    }
    if (type.equals("CREATE_AUCTION")) {
      writable();
      role(user, "SELLER", "ADMIN");
      Instant start = Instant.now().plusSeconds(number(p, "startDelaySeconds", 0, 604800));
      Instant end = start.plusSeconds(number(p, "durationSeconds", 1, 604800));
      Auction a = new Auction(item(p), user.getId(), start, end, bool(p, "antiSniping"));
      auctions.put(a.getId(), a);
      attach(a);
      changed("AUCTION_UPDATED", a);
      return envelope(a);
    }
    Auction a = find(text(p, "auctionId"));
    switch (type) {
      case "GET_AUCTION", "SUBSCRIBE" -> {
        return envelope(a);
      }
      case "GET_HISTORY" -> {
        List<BidTransaction> all = a.getHistory();
        int offset = p.has("offset") ? (int) number(p, "offset", 0, Integer.MAX_VALUE) : 0;
        int end = (int) Math.min(all.size(), (long) offset + 100);
        JsonArray bids = new JsonArray();
        for (int i = offset; i < end; i++) {
          bids.add(bidView(all.get(i)));
        }
        JsonObject out = new JsonObject();
        out.add("history", bids);
        out.addProperty("nextOffset", end < all.size() ? end : -1);
        out.addProperty("total", all.size());
        return out;
      }

      case "UPDATE_AUCTION" -> {
        writable();
        owner(user, a);
        Instant start = Instant.now().plusSeconds(number(p, "startDelaySeconds", 0, 604800));
        a.update(
            item(p),
            start,
            start.plusSeconds(number(p, "durationSeconds", 1, 604800)),
            bool(p, "antiSniping"));
      }
      case "DELETE_AUCTION" -> {
        writable();
        owner(user, a);
        if ((a.getStatus() != AuctionStatus.OPEN && a.getStatus() != AuctionStatus.CANCELED)
            || !a.getHistory().isEmpty()) {
          fail("CONFLICT", "Chỉ xóa phiên nháp/hủy chưa có bid");
        }
        auctions.remove(a.getId());
        persist();
        emit("AUCTION_DELETED", a);
        JsonObject out = new JsonObject();
        out.addProperty("deletedId", a.getId());
        return out;
      }
      case "START_AUCTION" -> {
        writable();
        owner(user, a);
        a.startNow(Instant.now());
      }
      case "CANCEL_AUCTION" -> {
        writable();
        owner(user, a);
        a.changeStatus(AuctionStatus.CANCELED);
      }
      case "PAY_AUCTION" -> {
        writable();
        if (!user.getId().equals(a.getLeaderId())) {
          fail("FORBIDDEN", "Chỉ người thắng xác nhận thanh toán");
        }
        a.changeStatus(AuctionStatus.PAID);
      }
      case "PLACE_BID" -> {
        writable();
        role(user, "BIDDER");
        a.placeBid(user.getId(), number(p, "amount", 1, Auction.MAX_AMOUNT), Instant.now());
        changed("NEW_BID", a);
        return envelope(a);
      }
      case "AUTO_BID" -> {
        writable();
        role(user, "BIDDER");
        a.configureAutoBid(
            user.getId(),
            number(p, "maxBid", 1, Auction.MAX_AMOUNT),
            number(p, "increment", 1, Auction.MAX_AMOUNT),
            Instant.now());
        changed("NEW_BID", a);
        return envelope(a);
      }
      case "REMOVE_AUTO_BID" -> {
        writable();
        role(user, "BIDDER");
        a.removeAutoBid(user.getId());
      }
      default -> throw new ApiException("BAD_REQUEST", "Loại yêu cầu không được hỗ trợ");
    }
    changed("AUCTION_UPDATED", a);
    return envelope(a);
  }

  /** Starts scheduled auctions and closes expired ones using server time. */
  public synchronized void tick() {
    if (storageFailed) {
      return;
    }
    Instant now = Instant.now();
    for (Auction a : auctions.values()) {
      if (a.getStatus() == AuctionStatus.OPEN && !now.isBefore(a.getStartTime())) {
        a.changeStatus(AuctionStatus.RUNNING);
        changed("AUCTION_UPDATED", a);
      }
      if (a.getStatus() == AuctionStatus.RUNNING && !now.isBefore(a.getEndTime())) {
        a.changeStatus(AuctionStatus.FINISHED);
        changed("AUCTION_FINISHED", a);
      }
    }
  }

  private Item item(JsonObject p) {
    return ItemFactory.create(
        ItemType.valueOf(text(p, "category")),
        text(p, "name"),
        text(p, "description"),
        number(p, "startingPrice", 1, Auction.MAX_AMOUNT),
        text(p, "extra1"),
        (int) number(p, "extra2", 0, 10_000_000));
  }

  private Auction find(String id) {
    Auction a = auctions.get(id);
    if (a == null) {
      fail("NOT_FOUND", "Không tìm thấy phiên");
    }
    return a;
  }

  private void owner(User user, Auction a) {
    role(user, "SELLER", "ADMIN");
    if (!user.getRole().equals("ADMIN") && !user.getId().equals(a.getSellerId())) {
      fail("FORBIDDEN", "Phiên thuộc người bán khác");
    }
  }

  private void role(User user, String... allowed) {
    if (!Arrays.asList(allowed).contains(user.getRole())) {
      fail("FORBIDDEN", "Vai trò không có quyền thực hiện");
    }
  }

  private void writable() {
    if (storageFailed) {
      fail("STORAGE_ERROR", "Lưu dữ liệu lỗi; dừng ghi, cần khởi động lại server");
    }
  }

  private void persist() {
    try {
      store.save(new FileStore.State(users.getUsers(), new ArrayList<>(auctions.values())));
    } catch (IOException e) {
      storageFailed = true;
      throw new ApiException("STORAGE_ERROR", "Không lưu được dữ liệu; không xác nhận thao tác");
    }
  }

  private void changed(String type, Auction a) {
    persist();
    boolean hasBids = observedBids.remove(a.getId());
    emit(hasBids ? "NEW_BID" : type, a);
  }

  private void emit(String type, Auction a) {
    observers.forEach(o -> o.accept(type, a));
  }

  /** Wraps an auction snapshot in the protocol response payload. */
  public synchronized JsonObject envelope(Auction a) {
    JsonObject out = new JsonObject();
    out.add("auction", view(a));
    return out;
  }

  /** Builds a public, versioned auction snapshot without private auto-bid caps. */
  public synchronized JsonObject view(Auction a) {
    JsonObject out = new JsonObject();
    out.addProperty("id", a.getId());
    out.addProperty("version", a.getVersion());
    out.addProperty("sellerId", a.getSellerId());
    out.addProperty("seller", users.username(a.getSellerId()));
    out.addProperty("name", a.getItem().getName());
    out.addProperty("description", a.getItem().getDescription());
    out.addProperty("category", a.getItem().getCategory());
    out.addProperty("itemInfo", a.getItem().printInfo());
    switch (a.getItem()) {
      case Electronics e -> {
        out.addProperty("extra1", e.getBrand());
        out.addProperty("extra2", e.getWarrantyMonths());
      }
      case Art art -> {
        out.addProperty("extra1", art.getArtist());
        out.addProperty("extra2", art.getYear());
      }
      case Vehicle v -> {
        out.addProperty("extra1", v.getBrand());
        out.addProperty("extra2", v.getMileageKm());
      }
      default -> throw new IllegalStateException("Loại sản phẩm không hỗ trợ");
    }
    out.addProperty("startingPrice", a.getItem().getStartingPrice());
    out.addProperty("currentPrice", a.getCurrentPrice());
    out.addProperty("status", a.getStatus().name());
    out.addProperty("leaderId", a.getLeaderId());
    out.addProperty("leader", users.username(a.getLeaderId()));
    if (a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID) {
      out.addProperty("winnerId", a.getLeaderId());
    }
    out.addProperty("startTime", a.getStartTime().toString());
    out.addProperty("endTime", a.getEndTime().toString());
    out.addProperty("antiSniping", a.isAntiSniping());
    JsonArray history = new JsonArray();
    List<BidTransaction> allBids = a.getHistory();
    out.addProperty("historyCount", allBids.size());
    for (BidTransaction bid : allBids.subList(Math.max(0, allBids.size() - 100), allBids.size())) {
      history.add(bidView(bid));
    }
    out.add("history", history);
    return out;
  }

  private JsonObject bidView(BidTransaction bid) {
    JsonObject b = new JsonObject();
    b.addProperty("bidderId", bid.getBidderId());
    b.addProperty("bidder", users.username(bid.getBidderId()));
    b.addProperty("amount", bid.getAmount());
    b.addProperty("time", bid.getTime().toString());
    return b;
  }

  /** Requires a bounded JSON string field. */
  public static String text(JsonObject p, String key) {
    JsonElement e = p.get(key);
    if (e == null
        || !e.isJsonPrimitive()
        || !e.getAsJsonPrimitive().isString()
        || e.getAsString().length() > 4096) {
      throw new ApiException("BAD_REQUEST", "Trường không hợp lệ: " + key);
    }
    return e.getAsString();
  }

  /** Requires an exact integer in the permitted range, rejecting decimal coercion. */
  public static long number(JsonObject p, String key, long min, long max) {
    JsonElement e = p.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
      throw new ApiException("BAD_REQUEST", "Cần số nguyên: " + key);
    }
    try {
      long n = e.getAsBigDecimal().longValueExact();
      if (n < min || n > max) {
        throw new ArithmeticException();
      }
      return n;
    } catch (ArithmeticException | NumberFormatException ex) {
      throw new ApiException("BAD_REQUEST", "Số ngoài phạm vi: " + key);
    }
  }

  private static boolean bool(JsonObject p, String key) {
    JsonElement e = p.get(key);
    if (e == null) {
      return false;
    }
    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
      throw new ApiException("BAD_REQUEST", "Cần boolean: " + key);
    }
    return e.getAsBoolean();
  }

  private static void fail(String code, String message) {
    throw new ApiException(code, message);
  }
}
