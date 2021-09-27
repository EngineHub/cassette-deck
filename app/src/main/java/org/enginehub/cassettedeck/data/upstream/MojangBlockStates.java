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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mojang's format for reporting block states.
 */
public record MojangBlockStates(
    Map<String, BlockData> mapping
) {
    public record BlockData(
        @Nullable Map<String, Set<String>> properties,
        List<BlockState> states
    ) {
    }

    public record BlockState(
        @Nullable Map<String, String> properties,
        @JsonProperty("default")
        @Nullable Boolean defaultState
    ) {
        @Override
        public @NotNull Boolean defaultState() {
            return defaultState == Boolean.TRUE;
        }
    }
}
