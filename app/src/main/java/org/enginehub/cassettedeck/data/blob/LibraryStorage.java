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

import org.enginehub.cassettedeck.data.upstream.MinecraftMetadata;
import org.enginehub.cassettedeck.util.DownloadHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Component
public class LibraryStorage {
    private final DiskStorage libraryStorage;
    private final RestTemplate restTemplate;

    public LibraryStorage(
        @Qualifier("library") DiskStorage libraryStorage,
        RestTemplate restTemplate
    ) {
        this.libraryStorage = libraryStorage;
        this.restTemplate = restTemplate;
    }

    public Path getLibraryJar(MinecraftMetadata.Download download) throws IOException {
        if (download.path() == null) {
            throw new IllegalArgumentException("Download must give a path");
        }
        libraryStorage.storeIfAbsent(download.path(), storageStream -> {
            byte[] content = DownloadHelper.downloadVerifiedBytes(restTemplate, download);
            storageStream.write(content);
        }).close();
        return Objects.requireNonNull(libraryStorage.retrievePath(download.path()), "Wasn't stored?");
    }
}
