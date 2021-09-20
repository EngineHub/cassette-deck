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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DiskStorage implements BlobStorage {
    // NB: This class assumes only a single process is running, and therefore only uses an in-process lock.
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
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
        Path ourKey = storageDir.resolve(key);
        if (ourKey.equals(storageDir)) {
            throw new IllegalArgumentException("Key path is the storage directory: " + key);
        }
        if (!ourKey.startsWith(storageDir)) {
            throw new IllegalArgumentException("Key breaks outside of storage directory: " + key);
        }
        return ourKey;
    }

    @Override
    public InputStream retrieve(String key) throws IOException {
        Path ourKey = ourKey(key);
        lock.readLock().lock();
        try {
            if (Files.isRegularFile(ourKey)) {
                // racy, but we _should_ be the sole owner of the storage
                // anyone cleaning our files can suffer
                return Files.newInputStream(ourKey);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void store(String key, OutputStreamConsumer consumer) throws IOException {
        Path ourKey = ourKey(key);
        Files.createDirectories(ourKey.getParent());
        lock.writeLock().lock();
        try {
            try (var output = Files.newOutputStream(ourKey)) {
                consumer.accept(output);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public InputStream storeIfAbsent(String key, OutputStreamConsumer consumer) throws IOException {
        Path ourKey = ourKey(key);
        while (true) {
            lock.readLock().lock();
            try {
                if (Files.isRegularFile(ourKey)) {
                    // racy, but we _should_ be the sole owner of the storage
                    // anyone cleaning our files can suffer
                    return Files.newInputStream(ourKey);
                }
            } finally {
                lock.readLock().unlock();
            }
            tryStore(ourKey, consumer);
        }
    }

    private void tryStore(Path ourKey, OutputStreamConsumer consumer) throws IOException {
        Files.createDirectories(ourKey.getParent());
        lock.writeLock().lock();
        try {
            // Atomically open the file for writing, failing if it's already there
            try (var output = Files.newOutputStream(ourKey, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                consumer.accept(output);
            } catch (FileAlreadyExistsException ignored) {
                // We'll catch the new file on our next go around.
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
