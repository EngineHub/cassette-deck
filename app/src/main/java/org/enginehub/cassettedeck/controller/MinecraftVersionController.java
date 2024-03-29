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

package org.enginehub.cassettedeck.controller;

import org.enginehub.cassettedeck.data.downstream.Cursor;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.enginehub.cassettedeck.service.MinecraftVersionService;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collection;

@RestController
@RequestMapping("/minecraft-versions")
public class MinecraftVersionController {
    private final MinecraftVersionService versionService;

    public MinecraftVersionController(MinecraftVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * All minecraft versions, sorted by release date, newest items first.
     *
     * @return the current cursor contents
     */
    @GetMapping("/list")
    public Cursor<MinecraftVersionEntry, Instant> listMinecraftVersions(
        @RequestParam(required = false) @Nullable Instant before,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return versionService.getAllVersions(before, limit);
    }

    /**
     * Get information about a particular version.
     *
     * @return the version info
     */
    @GetMapping("/{version}")
    public MinecraftVersionEntry getMinecraftVersion(
        @PathVariable String version
    ) {
        var entry = versionService.getVersion(version);
        if (entry == null) {
            throw new NotFoundException("minecraft-version");
        }
        return entry;
    }

    /**
     * Get information about a particular version, by data version.
     *
     * <p>
     * May return multiple results.
     * </p>
     *
     * @return the version info
     */
    @GetMapping("/find")
    public Collection<MinecraftVersionEntry> findMinecraftVersionsByDataVersion(
        @RequestParam int dataVersion
    ) {
        return versionService.findEntryByDataVersion(dataVersion);
    }
}
