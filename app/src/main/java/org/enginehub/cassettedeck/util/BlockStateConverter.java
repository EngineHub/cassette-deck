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

package org.enginehub.cassettedeck.util;

import com.google.common.collect.ImmutableMap;
import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.enginehub.cassettedeck.data.upstream.MojangBlockStates;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class BlockStateConverter {
    public static BlockStates convert(MojangBlockStates mojangBlockStates) {
        var mapping = ImmutableMap.<String, BlockStates.BlockStateData>builderWithExpectedSize(
            mojangBlockStates.mapping().size()
        );
        mojangBlockStates.mapping().forEach((id, state) -> {
            var defaultState = state.states().stream()
                .filter(MojangBlockStates.BlockState::defaultState)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Default state is missing from " + id));
            Map<String, BlockStates.BlockProperty> build = state.properties() == null
                ? Map.of()
                : getPropertyMap(state.properties());
            mapping.put(id, new BlockStates.BlockStateData(
                new BlockStates.BlockState(
                    id, Objects.requireNonNullElse(defaultState.properties(), Map.of())
                ),
                build
            ));
        });
        return new BlockStates(mapping.build());
    }

    private static Map<String, BlockStates.BlockProperty> getPropertyMap(Map<String, Set<String>> mojangProps) {
        var properties = ImmutableMap.<String, BlockStates.BlockProperty>builderWithExpectedSize(
            mojangProps.size()
        );
        mojangProps.forEach((propName, propValues) ->
            properties.put(propName, new BlockStates.BlockProperty(
                inferType(propValues),
                propValues
            ))
        );
        return properties.build();
    }

    private static final Set<String> BOOLEAN_VALUES = Set.of("true", "false");
    private static final Set<String> DIRECTION_VALUES = Set.of("north", "east", "south", "west", "up", "down");
    private static final Pattern INT_CHECK = Pattern.compile("[-+]?\\d+");

    private static BlockStates.BlockProperty.Type inferType(Set<String> propValues) {
        if (BOOLEAN_VALUES.containsAll(propValues)) {
            return BlockStates.BlockProperty.Type.BOOLEAN;
        }
        if (DIRECTION_VALUES.containsAll(propValues)) {
            return BlockStates.BlockProperty.Type.DIRECTION;
        }
        if (propValues.stream().allMatch(INT_CHECK.asMatchPredicate())) {
            return BlockStates.BlockProperty.Type.INT;
        }
        return BlockStates.BlockProperty.Type.ENUM;
    }
}
