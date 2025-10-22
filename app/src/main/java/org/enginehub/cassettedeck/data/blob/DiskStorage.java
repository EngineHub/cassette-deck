/*
 * Copyright (c) EngineHub <https://enginehub.org>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.enginehub.cassettedeck.data.blob;

import com.google.common.util.concurrent.Striped;
import org.apache.commons.io.function.IOConsumer;
import org.apache.commons.io.function.IOFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public class DiskStorage {

    private static final Logger LOGGER = LogManager.getLogger();

    private static Path writeToTempFile(Path ourKey, IOConsumer<Path> consumer) throws IOException {
        Path tempFile = Files.createTempFile(ourKey.getParent(), ourKey.getFileName().toString(), ".tmp");
        tempFile.toFile().deleteOnExit();
        try {
            consumer.accept(tempFile);
        } catch (Throwable t) {
            try {
                Files.delete(tempFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete temp file: {}", tempFile, e);
            }
            throw t;
        }
        return tempFile;
    }

    private static void touchKey(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to update last modified time for " + path, e);
        }
    }

    // NB: This class assumes only a single process is running, and therefore only uses an in-process lock.
    private final Striped<ReadWriteLock> locks = Striped.readWriteLock(32);
    private final Path storageDir;

    public DiskStorage(Path storageDir) {
        try {
            Files.createDirectories(storageDir);
            this.storageDir = storageDir.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path ourKey(String key) {
        Path ourKey = storageDir.resolve(key).toAbsolutePath();
        if (ourKey.equals(storageDir)) {
            throw new IllegalArgumentException("Key path is the storage directory: " + key);
        }
        if (!ourKey.startsWith(storageDir)) {
            throw new IllegalArgumentException("Key breaks outside of storage directory: " + key);
        }
        return ourKey;
    }

    public <R extends @Nullable Object> R usePath(String key, IOFunction<Path, R> consumer) throws IOException {
        Path ourKey = ourKey(key);
        Lock lock = locks.get(ourKey).readLock();
        lock.lock();
        try {
            if (!Files.isRegularFile(ourKey)) {
                throw new IOException("No such file: " + ourKey);
            }
            touchKey(ourKey);
            return consumer.apply(ourKey);
        } finally {
            lock.unlock();
        }
    }

    public <R extends @Nullable Object> R usePaths(List<String> key, IOFunction<List<Path>, R> consumer) throws IOException {
        List<Path> ourKeys = key.stream().map(this::ourKey).toList();
        List<Lock> readLocks = new ArrayList<>(ourKeys.size());
        for (ReadWriteLock lock : locks.bulkGet(ourKeys)) {
            readLocks.add(lock.readLock());
        }
        for (int i = 0; i < ourKeys.size(); i++) {
            try {
                readLocks.get(i).lock();
            } catch (Throwable t) {
                // This is really paranoid, but we don't want to leave any locks held
                // Even this isn't exactly perfect, if unlock throws, we're in trouble. But that should never happen.
                for (int j = i - 1; j >= 0; j--) {
                    readLocks.get(j).unlock();
                }
                throw t;
            }
        }
        try {
            for (Path ourKey : ourKeys) {
                if (!Files.isRegularFile(ourKey)) {
                    throw new IOException("No such file: " + ourKey);
                }
                touchKey(ourKey);
            }
            return consumer.apply(ourKeys);
        } finally {
            for (Lock lock : readLocks) {
                lock.unlock();
            }
        }
    }

    // This API is Linux-specific in design, but we don't care about Windows.
    // Specifically, we rely on atomic moves and the ability to delete open files without issue.
    public @Nullable InputStream retrieve(String key) throws IOException {
        Path ourKey = ourKey(key);
        Lock lock = locks.get(ourKey).readLock();
        lock.lock();
        try {
            if (Files.isRegularFile(ourKey)) {
                touchKey(ourKey);
                // racy, but we _should_ be the sole owner of the storage
                // anyone cleaning our files can suffer
                return Files.newInputStream(ourKey);
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    public void store(String key, IOConsumer<Path> consumer) throws IOException {
        Path ourKey = ourKey(key);
        Files.createDirectories(ourKey.getParent());
        Lock lock = locks.get(ourKey).writeLock();
        lock.lock();
        try {
            Path tempFile = writeToTempFile(ourKey, consumer);
            try {
                Files.move(tempFile, ourKey, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                try {
                    Files.delete(tempFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete temp file: {}", tempFile, e);
                }
                throw t;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * If there is no blob for the given key, use {@code consumer} to fill it, then return a stream to get the contents
     * of the blob.
     *
     * @param key the key
     * @param consumer the blob provider
     * @return the content of the blob
     * @throws IOException if there is an I/O error
     */
    public InputStream storeIfAbsent(String key, IOConsumer<Path> consumer) throws IOException {
        Path ourKey = ourKey(key);
        while (true) {
            Lock lock = locks.get(ourKey).writeLock();
            lock.lock();
            try {
                if (Files.isRegularFile(ourKey)) {
                    // racy, but we _should_ be the sole owner of the storage
                    // anyone cleaning our files can suffer
                    return Files.newInputStream(ourKey);
                }
                tryStore(ourKey, consumer);
            } finally {
                lock.unlock();
            }
        }
    }

    private void tryStore(Path ourKey, IOConsumer<Path> consumer) throws IOException {
        Files.createDirectories(ourKey.getParent());
        Path tempFile = writeToTempFile(ourKey, consumer);
        try {
            Files.move(tempFile, ourKey, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            // someone else beat us to it
            Files.delete(tempFile);
        } catch (Throwable t) {
            try {
                Files.delete(tempFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete temp file: {}", tempFile, e);
            }
            throw t;
        }
    }
}
