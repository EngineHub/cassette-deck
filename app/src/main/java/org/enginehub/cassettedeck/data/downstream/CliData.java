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

import java.util.List;
import java.util.Map;

public record CliData(
    Map<String, BlockManifest> blocks,
    List<String> items,
    List<String> entities,
    List<String> biomes,
    Map<String, List<String>> blocktags,
    Map<String, List<String>> itemtags,
    Map<String, List<String>> entitytags
) {
    public record BlockManifest(String defaultstate, Map<String, BlockProperty> properties) {
    }

    public record BlockProperty(List<String> values, String type) {
    }

}
