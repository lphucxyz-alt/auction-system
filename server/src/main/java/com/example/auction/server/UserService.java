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

public class UserService {
  private static final int ITERATIONS = 120_000;
  private final Map<String, User> users = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public User register(String username, String password, String role) {
    if (username == null || username.isBlank() || password == null || password.length() < 6) {
      throw new AuctionException("Tên đăng nhập trống hoặc mật khẩu ngắn hơn 6 ký tự");
    }
    String hash = hash(password);
    User user = switch (role) {
      case "BIDDER" -> new Bidder(username, hash);
      case "SELLER" -> new Seller(username, hash);
      case "ADMIN" -> new Admin(username, hash);
      default -> throw new AuctionException("Vai trò không hợp lệ: " + role);
    };
    // putIfAbsent là nguyên tử: hai người đăng ký cùng tên cùng lúc thì chỉ một người thắng
    if (users.putIfAbsent(username, user) != null) {
      throw new AuctionException("Tên đăng nhập đã tồn tại");
    }
    return user;
  }

  public User login(String username, String password) {
    User user = username == null ? null : users.get(username);
    if (user == null || password == null || !verify(password, user.getPasswordHash())) {
      // Cùng một thông báo cho cả hai trường hợp, để không lộ tên nào tồn tại
      throw new AuthenticationException("Sai tên đăng nhập hoặc mật khẩu");
    }
    return user;
  }

  private String hash(String password) {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt) + ":"
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
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
