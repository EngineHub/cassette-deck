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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.service.BlockStatesService;
import org.enginehub.cassettedeck.service.MinecraftVersionService;
import org.enginehub.cassettedeck.util.BlockStateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MinecraftVersionPoller {
    private static final String MINECRAFT_MANIFEST_URL =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Logger LOGGER = LogManager.getLogger();

    private final Semaphore loadingLimit = new Semaphore(4);
    private final MinecraftVersionService minecraftVersionService;
    private final BlockStatesService blockStatesService;
    private final RestTemplate restTemplate;
    private final ExtraMetadataLoader loader;
    private final Executor workExecutor;

    public MinecraftVersionPoller(MinecraftVersionService minecraftVersionService,
                                  BlockStatesService blockStatesService,
                                  RestTemplate restTemplate,
                                  ExtraMetadataLoader loader,
                                  @Qualifier("applicationTaskExecutor") Executor workExecutor) {
        this.minecraftVersionService = minecraftVersionService;
        this.blockStatesService = blockStatesService;
        this.restTemplate = restTemplate;
        this.loader = loader;
        this.workExecutor = workExecutor;
    }

    @Scheduled(fixedDelayString = "${minecraft-version.poll.interval}")
    public void poll() {
        LOGGER.info("Polling Minecraft Version Manifest");
        try {
            doPoll();
        } catch (Throwable e) {
            LOGGER.warn("Failed to poll Minecraft Version Manifest", e);
        }
    }

    private void doPoll() {
        var manifest = restTemplate.getForObject(MINECRAFT_MANIFEST_URL, VersionManifest.class);
        Objects.requireNonNull(manifest, "manifest was null");
        if (manifest.versions().isEmpty()) {
            return;
        }
        Map<String, VersionManifest.Version> needed = manifest.versions().stream()
            .collect(Collectors.toMap(VersionManifest.Version::id, Function.identity()));
        // Filter to only what we don't have
        needed.keySet().retainAll(minecraftVersionService.findMissingVersions(needed.keySet()));
        for (VersionManifest.Version next : needed.values()) {
            LOGGER.info(() -> "[" + next.id() + "] Submitting for metadata filling");
            loadingLimit.acquireUninterruptibly();
            try {
                var future = CompletableFuture.runAsync(() -> {
                    LOGGER.info(() -> "[" + next.id() + "] Starting metadata filling");
                    var result = loader.load(new MinecraftVersionEntry(
                        next.id(),
                        null,
                        next.releaseTime(),
                        URLDecoder.decode(next.url(), StandardCharsets.UTF_8),
                        null,
                        false
                    ));
                    if (result.fullEntry().hasDataGenInfo()) {
                        Objects.requireNonNull(result.blockStates(), "Has Data Gen, but no block states given");
                        LOGGER.info(() -> "[" + next.id() + "] Storing block state JSON file");
                        try {
                            blockStatesService.setBlockStates(
                                result.fullEntry().dataVersion(),
                                BlockStateConverter.convert(result.blockStates())
                            );
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    LOGGER.info(() -> "[" + next.id() + "] Inserting into database");
                    minecraftVersionService.insert(List.of(result.fullEntry()));
                }, workExecutor);
                future.whenComplete((__, ex) -> {
                    if (ex != null) {
                        LOGGER.warn(() -> "[" + next.id() + "] Failed to load version", ex);
                    } else {
                        LOGGER.info(() -> "[" + next.id() + "] Fully loaded!");
                    }
                });
                // Ensure this gets released no matter what
                future.whenComplete((__, ___) -> loadingLimit.release());
            } catch (Throwable t) {
                loadingLimit.release();
                throw t;
            }
        }
    }
}
