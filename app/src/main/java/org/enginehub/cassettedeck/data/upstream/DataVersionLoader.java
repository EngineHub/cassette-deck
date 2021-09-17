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

package org.enginehub.cassettedeck.data.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.exception.DownloadException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

@Component
public class DataVersionLoader {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    public DataVersionLoader(ObjectMapper mapper, RestTemplateBuilder restTemplateBuilder) {
        this.mapper = mapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MinecraftMetadata(
        Downloads downloads
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Downloads(
        Download server
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Download(
        String sha1,
        int size,
        String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Version(
        @JsonProperty("world_version")
        int worldVersion
    ) {
    }

    public int load(MinecraftVersionEntry entry) throws DownloadException {
        LOGGER.info(() -> "[" + entry.getVersion() + "] Starting load for data version");
        byte[] serverJarBytes;
        try {
            serverJarBytes = getServerJarBytes(entry);
        } catch (RestClientException e) {
            throw new DownloadException(DownloadException.Kind.IO_ERROR, e);
        }
        LOGGER.info(() -> "[" + entry.getVersion() + "] Downloaded + verified server JAR bytes");
        try {
            var zf = new ZipFile(new SeekableInMemoryByteChannel(serverJarBytes));
            ZipArchiveEntry versionJsonEntry = zf.getEntry("version.json");
            if (versionJsonEntry == null) {
                throw new IllegalStateException("JAR is missing version.json");
            }
            var versionData = mapper.readValue(
                zf.getInputStream(versionJsonEntry).readAllBytes(),
                Version.class
            );
            LOGGER.info(() -> "[" + entry.getVersion() + "] Acquired data version: " + versionData.worldVersion);
            return versionData.worldVersion;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] getServerJarBytes(MinecraftVersionEntry entry) {
        var metadata = restTemplate.getForObject(entry.getUrl(), MinecraftMetadata.class);
        Objects.requireNonNull(metadata, "metadata is null");
        byte[] serverJarBytes = restTemplate.getForObject(metadata.downloads.server.url, byte[].class);
        Objects.requireNonNull(serverJarBytes, "serverJarBytes is null");
        if (serverJarBytes.length != metadata.downloads.server.size) {
            throw new DownloadException(
                DownloadException.Kind.LENGTH_MISMATCH,
                new AssertionError(serverJarBytes.length + " != " + metadata.downloads.server.size)
            );
        }
        // Mojang uses sha1, we have to as well
        @SuppressWarnings("deprecation")
        var localSha1 = Hashing.sha1().hashBytes(serverJarBytes).toString();
        if (!localSha1.equals(metadata.downloads.server.sha1)) {
            throw new DownloadException(
                DownloadException.Kind.HASH_MISMATCH,
                new AssertionError(localSha1 + " != " + metadata.downloads.server.sha1)
            );
        }
        return serverJarBytes;
    }
}
