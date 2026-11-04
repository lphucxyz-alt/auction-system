package com.example.auction.server.dao;

import com.example.auction.common.model.Auction;
import com.example.auction.common.model.User;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Server-only serialization DAO. Atomic replacement and a process lock protect the snapshot. */
public final class FileStore implements AutoCloseable {
  /** Versioned server-local serialization snapshot. */
  public record State(int schema, ArrayList<User> users, ArrayList<Auction> auctions)
      implements Serializable {
    public State(List<User> users, List<Auction> auctions) {
      this(1, new ArrayList<>(users), new ArrayList<>(auctions));
    }
  }

  private final Path file;
  private final FileChannel channel;
  private final FileLock lock;
  private boolean closed;

  /** Creates an instance with the supplied identity and configuration. */
  public FileStore(Path directory) throws IOException {
    Files.createDirectories(directory);
    file = directory.resolve("auction-state.bin");
    channel =
        FileChannel.open(
            directory.resolve("server.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired;
    try {
      acquired = channel.tryLock();
    } catch (IOException | RuntimeException e) {
      channel.close();
      throw new IOException("Data directory đang được dùng", e);
    }
    if (acquired == null) {
      channel.close();
      throw new IOException("Data directory đang được dùng");
    }
    lock = acquired;
  }

  /** Loads the local snapshot with an object filter, preserving corrupt originals on error. */
  public synchronized State load() throws IOException {
    checkOpen();
    if (!Files.exists(file)) {
      return new State(List.of(), List.of());
    }
    try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
      in.setObjectInputFilter(
          info -> {
            if (info.arrayLength() > 1_000_000
                || info.depth() > 40
                || info.references() > 1_000_000
                || info.streamBytes() > 100_000_000) {
              return ObjectInputFilter.Status.REJECTED;
            }
            Class<?> type = info.serialClass();
            if (type == null) {
              return ObjectInputFilter.Status.UNDECIDED;
            }
            while (type.isArray()) {
              type = type.getComponentType();
            }
            return type.isPrimitive()
                    || type.getName().startsWith("com.example.auction.common.model.")
                    || type == State.class
                    || type.getName().startsWith("java.lang.")
                    || type.getName().startsWith("java.time.")
                    || type.getName().startsWith("java.util.")
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
          });
      Object value = in.readObject();
      if (!(value instanceof State state) || state.schema() != 1) {
        throw new IOException("Định dạng snapshot không được hỗ trợ");
      }
      return state;
    } catch (ClassNotFoundException | RuntimeException e) {
      throw new IOException("Không đọc được snapshot; dữ liệu gốc được giữ nguyên", e);
    }
  }

  /** Writes and fsyncs a temporary snapshot before atomic replacement. */
  public synchronized void save(State state) throws IOException {
    checkOpen();
    Path temp = Files.createTempFile(file.getParent(), "snapshot-", ".tmp");
    try {
      try (FileOutputStream stream = new FileOutputStream(temp.toFile());
          ObjectOutputStream out = new ObjectOutputStream(stream)) {
        out.writeObject(state);
        out.flush();
        stream.getFD().sync();
      }
      // Same filesystem; fail instead of falling back to a non-atomic overwrite.
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      lock.release();
    } finally {
      channel.close();
    }
  }

  private void checkOpen() throws IOException {
    if (closed || !channel.isOpen() || !lock.isValid()) {
      throw new IOException("Kho dữ liệu đã đóng");
    }
  }
}
