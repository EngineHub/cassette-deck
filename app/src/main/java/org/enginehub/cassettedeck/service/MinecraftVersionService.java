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

package org.enginehub.cassettedeck.service;

import org.enginehub.cassettedeck.data.downstream.Cursor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

public interface MinecraftVersionService {
    /**
     * Given a set of known versions, find which are missing in the database.
     *
     * @param knownVersions the known versions
     * @return the versions not in the database
     */
    Set<String> findMissingVersions(Set<String> knownVersions);

    Cursor<String, Instant> getAllVersions(@Nullable Instant beforeDate, int limit);

    void insert(String version, Instant releaseInstant);
}
