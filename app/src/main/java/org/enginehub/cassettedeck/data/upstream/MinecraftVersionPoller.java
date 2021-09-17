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
import org.enginehub.cassettedeck.service.MinecraftVersionService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MinecraftVersionPoller {
    private static final String MINECRAFT_MANIFEST_URL =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Logger LOGGER = LogManager.getLogger();

    private final MinecraftVersionService minecraftVersionService;
    private final RestTemplate restTemplate;

    public MinecraftVersionPoller(MinecraftVersionService minecraftVersionService,
                                  RestTemplateBuilder restTemplateBuilder) {
        this.minecraftVersionService = minecraftVersionService;
        this.restTemplate = restTemplateBuilder.build();
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
        var batch = new ArrayList<MinecraftVersionEntry>(needed.size());
        for (VersionManifest.Version next : needed.values()) {
            LOGGER.info(() -> "Adding " + next.id() + " to the database");
            batch.add(new MinecraftVersionEntry(
                next.id(),
                null,
                next.releaseTime(),
                URLDecoder.decode(next.url(), StandardCharsets.UTF_8)
            ));
        }
        minecraftVersionService.insert(batch);
    }
}
