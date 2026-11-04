package com.example.auction.server.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStoreTest {
  @TempDir Path directory;

  @Test
  void closedStoreCannotWriteAfterAnotherStoreAcquiresLock() throws Exception {
    FileStore old = new FileStore(directory);
    old.close();
    try (FileStore current = new FileStore(directory)) {
      assertThrows(IOException.class, () -> old.save(new FileStore.State(List.of(), List.of())));
      assertThrows(IOException.class, old::load);
      current.save(new FileStore.State(List.of(), List.of()));
    }
  }

  @Test
  void closeCanBeCalledTwice() throws Exception {
    FileStore store = new FileStore(directory);
    store.close();
    assertDoesNotThrow(store::close);
  }

  @Test
  void oversizedArrayIsRejectedByFilterBeforeLoadingTheObject() throws Exception {
    try (FileStore store = new FileStore(directory)) {
      try (var output =
          new ObjectOutputStream(Files.newOutputStream(directory.resolve("auction-state.bin")))) {
        output.writeObject(new byte[1_000_001]);
      }
      assertThrows(InvalidClassException.class, store::load);
    }
  }
}
