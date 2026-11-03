package com.example.auction.server;

import com.example.auction.common.exception.AuctionException;
import com.example.auction.common.exception.AuthenticationException;
import com.example.auction.common.model.Admin;
import com.example.auction.common.model.Bidder;
import com.example.auction.common.model.Seller;
import com.example.auction.common.model.User;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Thread-safe registration and PBKDF2 account authentication. */
public class UserService {
  private static final int ITERATIONS = 120_000;
  private final Map<String, User> users = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  /** Validates and registers a unique account, hashing its password with a random salt. */
  public User register(String username, String password, String role) {
    if (username == null
        || !username.matches("[A-Za-z0-9_]{2,32}")
        || password == null
        || password.length() < 6
        || password.length() > 128
        || role == null) {
      throw new AuctionException("Tên đăng nhập trống hoặc mật khẩu ngắn hơn 6 ký tự");
    }
    String hash = hash(password);
    User user = createUser(role, username, hash);
    // putIfAbsent là nguyên tử: hai người đăng ký cùng tên cùng lúc thì chỉ một người thắng
    if (users.putIfAbsent(username, user) != null) {
      throw new AuctionException("Tên đăng nhập đã tồn tại");
    }
    return user;
  }

  /** Authenticates an existing account without revealing which credential was incorrect. */
  public User login(String username, String password) {
    User user = username == null ? null : users.get(username);
    if (user == null || password == null || !verify(password, user.getPasswordHash())) {
      // Cùng một thông báo cho cả hai trường hợp, để không lộ tên nào tồn tại
      throw new AuthenticationException("Sai tên đăng nhập hoặc mật khẩu");
    }
    return user;
  }

  public java.util.List<User> getUsers() {
    return java.util.List.copyOf(users.values());
  }

  /** Restores accounts from the trusted server-local snapshot. */
  public void restore(java.util.List<User> saved) {
    for (User user : saved) {
      users.put(user.getUsername(), user);
    }
  }

  /** Looks up the display name for an entity identifier. */
  public String username(String id) {
    return users.values().stream()
        .filter(u -> u.getId().equals(id))
        .map(User::getUsername)
        .findFirst()
        .orElse("");
  }

  private User createUser(String role, String username, String hash) {
    return switch (role) {
      case "BIDDER" -> new Bidder(username, hash);
      case "SELLER" -> new Seller(username, hash);
      case "ADMIN" -> new Admin(username, hash);
      default -> throw new AuctionException("Vai trò không hợp lệ: " + role);
    };
  }

  private String hash(String password) {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(pbkdf2(password, salt));
  }

  private boolean verify(String password, String stored) {
    String[] parts = stored.split(":");
    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] expected = Base64.getDecoder().decode(parts[1]);
    return MessageDigest.isEqual(expected, pbkdf2(password, salt)); // so sánh thời gian cố định
  }

  private byte[] pbkdf2(String password, byte[] salt) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
      try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .getEncoded();
      } finally {
        spec.clearPassword();
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
