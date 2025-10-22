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
import org.apache.commons.io.function.IOFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.enginehub.cassettedeck.data.blob.LibraryStorage;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.exception.DownloadException;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class ExtraMetadataLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    // Data versions not indexable automatically, but needed downstream
    private static final Map<String, Integer> KNOWN_DATA_VERSIONS = Map.of(
        "1.13.2", 1631,
        "1.12.2", 1343
    );
    // 1.13.2 release date, cut-off for data gen
    private static final Instant DATA_GEN_AFTER = Instant.parse("2018-10-22T00:00:00+00:00");

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final LibraryStorage libraryStorage;
    private final DataGeneratorExecutor.Config dataGenConfig;
    private final Semaphore downloadSemaphore;

    public ExtraMetadataLoader(
        ObjectMapper mapper,
        RestTemplate restTemplate,
        LibraryStorage libraryStorage,
        DataGeneratorExecutor.Config dataGenConfig,
        @Qualifier("concurrentDownloads") Semaphore downloadSemaphore
    ) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.libraryStorage = libraryStorage;
        this.dataGenConfig = dataGenConfig;
        this.downloadSemaphore = downloadSemaphore;
    }

    public record Result(
        MinecraftVersionEntry fullEntry,
        @Nullable MojangBlockStates blockStates
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Version(
        @JsonProperty("world_version")
        int worldVersion
    ) {
    }

    public Result load(MinecraftVersionEntry entry) throws DownloadException {
        downloadSemaphore.acquireUninterruptibly();
        MinecraftMetadata metadata;
        try {
            metadata = restTemplate.getForObject(entry.url(), MinecraftMetadata.class);
        } finally {
            downloadSemaphore.release();
        }
        Objects.requireNonNull(metadata, "metadata is null");
        LOGGER.info(() -> "[" + entry.version() + "] Starting load for JAR bytes");
        boolean doDataGen = metadata.type() == MinecraftVersionType.RELEASE
            && entry.releaseDate().isAfter(DATA_GEN_AFTER);
        try {
            // This is bugged right now
            //noinspection DataFlowIssue
            int dataVersion = useMinecraftJarFile(entry, metadata, doDataGen, minecraftJarPath -> {
                LOGGER.info(() -> "[" + entry.version() + "] Downloaded + verified Minecraft JAR bytes");

                int dv;
                try (var zf = new ZipFile(minecraftJarPath.toFile())) {
                    dv = getDataVersion(entry, zf);
                    if (doDataGen) {
                        if (zf.getEntry("net/minecraft/data/Main.class") == null) {
                            throw new IllegalStateException(
                                "Need to data gen, but the data gen Main is missing from the " + entry.version() + " JAR"
                            );
                        }
                    }
                }
                return dv;
            });

            MojangBlockStates blockStates;
            if (doDataGen) {
                blockStates = new DataGeneratorExecutor(
                    dataGenConfig, metadata, metadata.downloads().client().fillInPath(entry.version(), "client")
                ).generateBlockStates();
            } else {
                blockStates = null;
            }

            return new Result(
                new MinecraftVersionEntry(
                    entry.version(),
                    dataVersion,
                    entry.releaseDate(),
                    entry.url(),
                    metadata.downloads().client().url(),
                    entry.type(),
                    doDataGen
                ),
                blockStates
            );
        } catch (RestClientException | IOException e) {
            throw new DownloadException(DownloadException.Kind.IO_ERROR, e);
        }
    }

    private int getDataVersion(MinecraftVersionEntry entry, ZipFile zf) throws IOException {
        if (KNOWN_DATA_VERSIONS.containsKey(entry.version())) {
            return KNOWN_DATA_VERSIONS.get(entry.version());
        }
        ZipEntry versionJsonEntry = zf.getEntry("version.json");
        if (versionJsonEntry == null) {
            // This is expected.
            LOGGER.info(() -> "[" + entry.version() + "] Missing version.json, entering -1 for data version");
            return -1;
        }
        var versionData = mapper.readValue(
            zf.getInputStream(versionJsonEntry).readAllBytes(),
            Version.class
        );
        LOGGER.info(() -> "[" + entry.version() + "] Acquired data version: " + versionData.worldVersion);
        return versionData.worldVersion;
    }

    private <R extends @Nullable Object> R useMinecraftJarFile(
        MinecraftVersionEntry entry,
        MinecraftMetadata metadata,
        boolean forceClient,
        IOFunction<Path, R> function
    ) {
        record DownloadWithName(
            String name,
            MinecraftMetadata.Download download
        ) {
        }
        var client = new DownloadWithName("client", metadata.downloads().client());
        var server = new DownloadWithName("server", metadata.downloads().server());
        DownloadWithName chosenEntry;
        if (forceClient) {
            chosenEntry = client;
            Objects.requireNonNull(chosenEntry.download, () -> "No client JAR available for " + entry.version());
        } else {
            chosenEntry = Stream.of(client, server)
                .filter(dwn -> dwn.download != null)
                .min(Comparator.comparing(dwn -> dwn.download.size()))
                .orElseThrow();
        }
        LOGGER.info(() -> "[" + entry.version() + "] Our JAR url is " + chosenEntry.download().url());
        try {
            var withPath = chosenEntry.download().fillInPath(entry.version(), chosenEntry.name());
            return libraryStorage.useLibraryJar(withPath, function);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
