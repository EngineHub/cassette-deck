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

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import org.apache.commons.io.function.IOFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.enginehub.cassettedeck.CassetteDeck;
import org.enginehub.cassettedeck.data.upstream.MinecraftMetadata;
import org.enginehub.cassettedeck.exception.DownloadException;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class LibraryStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    private final DiskStorage libraryStorage;
    private final HttpClient httpClient;

    public LibraryStorage(
        @Qualifier("library") DiskStorage libraryStorage,
        HttpClient httpClient
    ) {
        this.libraryStorage = libraryStorage;
        this.httpClient = httpClient;
    }

    public <R extends @Nullable Object> R useLibraryJar(
        MinecraftMetadata.Download download,
        IOFunction<Path, R> function
    ) throws IOException {
        if (download.path() == null) {
            throw new IllegalArgumentException("Download must give a path");
        }
        preparePath(download);
        return libraryStorage.usePath(download.path(), function);
    }

    public <R extends @Nullable Object> R useLibraryJars(
        List<MinecraftMetadata.Download> download,
        IOFunction<List<Path>, R> function
    ) throws IOException {
        if (!download.stream().allMatch(d -> d.path() != null)) {
            throw new IllegalArgumentException("Downloads must give a path");
        }
        for (MinecraftMetadata.Download d : download) {
            preparePath(d);
        }
        return libraryStorage.usePaths(download.stream().map(MinecraftMetadata.Download::path).toList(), function);
    }

    private void preparePath(MinecraftMetadata.Download download) throws IOException {
        libraryStorage.storeIfAbsent(download.path(), destination -> {
            HttpResponse<Path> response;
            try {
                response = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(download.url()))
                        .header(HttpHeaders.USER_AGENT, CassetteDeck.USER_AGENT)
                        .build(),
                    HttpResponse.BodyHandlers.ofFile(destination)
                );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String output;
                try {
                    output = Files.readString(destination);
                } catch (IOException e) {
                    output = "<Failed to read error file: " + e.getMessage() + ">";
                }
                LOGGER.warn("Failed to download library: {}", output);
                throw new DownloadException(
                    DownloadException.Kind.IO_ERROR,
                    new AssertionError("HTTP " + response.statusCode())
                );
            }
            long size = Files.size(destination);
            if (size != download.size()) {
                throw new DownloadException(
                    DownloadException.Kind.LENGTH_MISMATCH,
                    new AssertionError(size + " != " + download.size())
                );
            }
            // Mojang uses sha1, we have to as well
            @SuppressWarnings("deprecation")
            var localSha1 = Hashing.sha1().hashObject(destination, (from, into) -> {
                try {
                    Files.copy(from, Funnels.asOutputStream(into));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toString();
            if (!localSha1.equals(download.sha1())) {
                throw new DownloadException(
                    DownloadException.Kind.HASH_MISMATCH,
                    new AssertionError(localSha1 + " != " + download.sha1())
                );
            }
        }).close();
    }
}
