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
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MinecraftMetadata(
    Downloads downloads,
    List<Library> libraries,
    String type
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Downloads(
        Download client,
        Download server
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Download(
        String sha1,
        int size,
        String url,
        @Nullable String path
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Library(
        Downloads downloads,
        String name
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Downloads(
            Download artifact
        ) {
        }
    }
}
