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
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class ExtraMetadataLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    // Data versions not indexable automatically, but needed downstream
    private static final Map<String, Integer> KNOWN_DATA_VERSIONS = Map.of(
        "1.13.2", 1631,
        "1.12.2", 1343
    );

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    public ExtraMetadataLoader(ObjectMapper mapper, RestTemplateBuilder restTemplateBuilder) {
        this.mapper = mapper;
        this.restTemplate = restTemplateBuilder
            .defaultHeader("User-Agent", "cassette-deck")
            .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MinecraftMetadata(
        Downloads downloads
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Downloads(
        Download client,
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

    public MinecraftVersionEntry load(MinecraftVersionEntry entry) throws DownloadException {
        var metadata = restTemplate.getForObject(entry.getUrl(), MinecraftMetadata.class);
        Objects.requireNonNull(metadata, "metadata is null");
        int dataVersion = getDataVersion(entry, metadata);
        return new MinecraftVersionEntry(
            entry.getVersion(),
            dataVersion,
            entry.getReleaseDate(),
            entry.getUrl(),
            metadata.downloads.client.url
        );
    }

    private int getDataVersion(MinecraftVersionEntry entry, MinecraftMetadata metadata) {
        if (KNOWN_DATA_VERSIONS.containsKey(entry.getVersion())) {
            return KNOWN_DATA_VERSIONS.get(entry.getVersion());
        }
        LOGGER.info(() -> "[" + entry.getVersion() + "] Starting load for data version");
        byte[] minecraftJarBytes;
        try {
            minecraftJarBytes = getMinecraftJarBytes(entry, metadata);
        } catch (RestClientException e) {
            throw new DownloadException(DownloadException.Kind.IO_ERROR, e);
        }
        LOGGER.info(() -> "[" + entry.getVersion() + "] Downloaded + verified Minecraft JAR bytes");
        try (var zf = new ZipFile(new SeekableInMemoryByteChannel(minecraftJarBytes))) {
            ZipArchiveEntry versionJsonEntry = zf.getEntry("version.json");
            if (versionJsonEntry == null) {
                // This is expected.
                LOGGER.info(() -> "[" + entry.getVersion() + "] Missing version.json, entering -1");
                return -1;
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

    private byte[] getMinecraftJarBytes(MinecraftVersionEntry entry, MinecraftMetadata metadata) {
        var smallestEntry = Stream.of(metadata.downloads.client, metadata.downloads.server)
            .filter(Objects::nonNull)
            .min(Comparator.comparing(Download::size))
            .orElseThrow();
        LOGGER.info(() -> "[" + entry.getVersion() + "] Smallest JAR url is " + smallestEntry.url);
        byte[] jarBytes = restTemplate.getForObject(smallestEntry.url, byte[].class);
        Objects.requireNonNull(jarBytes, "jarBytes is null");
        if (jarBytes.length != smallestEntry.size) {
            throw new DownloadException(
                DownloadException.Kind.LENGTH_MISMATCH,
                new AssertionError(jarBytes.length + " != " + smallestEntry.size)
            );
        }
        // Mojang uses sha1, we have to as well
        @SuppressWarnings("deprecation")
        var localSha1 = Hashing.sha1().hashBytes(jarBytes).toString();
        if (!localSha1.equals(smallestEntry.sha1)) {
            throw new DownloadException(
                DownloadException.Kind.HASH_MISMATCH,
                new AssertionError(localSha1 + " != " + smallestEntry.sha1)
            );
        }
        return jarBytes;
    }
}
