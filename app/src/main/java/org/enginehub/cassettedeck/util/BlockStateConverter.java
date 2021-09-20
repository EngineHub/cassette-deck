package org.enginehub.cassettedeck.util;

import com.google.common.collect.ImmutableMap;
import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.enginehub.cassettedeck.data.upstream.MojangBlockStates;

import java.util.Map;
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
                    id, defaultState.properties()
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
