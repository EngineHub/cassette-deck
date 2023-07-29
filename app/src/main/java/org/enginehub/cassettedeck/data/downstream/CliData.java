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

package org.enginehub.cassettedeck.data.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record CliData(
    @JsonProperty(required = true)
    Map<String, BlockManifest> blocks,
    @JsonProperty(required = true)
    List<String> items,
    @JsonProperty(required = true)
    List<String> entities,
    @JsonProperty(required = true)
    List<String> biomes,
    @JsonProperty(value = "blocktags", required = true)
    Map<String, List<String>> blockTags,
    @JsonProperty(value = "itemtags", required = true)
    Map<String, List<String>> itemTags,
    @JsonProperty(value = "entitytags", required = true)
    Map<String, List<String>> entityTags
) {
    public record BlockManifest(
        @JsonProperty(value = "defaultstate", required = true)
        String defaultState,
        @JsonProperty(required = true)
        Map<String, BlockProperty> properties
    ) {
    }

    public record BlockProperty(
        @JsonProperty(required = true)
        List<String> values,
        @JsonProperty(required = true)
        String type
    ) {
    }

}
