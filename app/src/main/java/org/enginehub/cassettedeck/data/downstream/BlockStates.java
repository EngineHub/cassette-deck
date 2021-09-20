package org.enginehub.cassettedeck.data.downstream;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Map;
import java.util.Set;

public record BlockStates(
    @JsonUnwrapped
    Map<String, BlockStateData> mapping
) {
    public record BlockStateData(
        BlockState defaultState,
        Map<String, BlockProperty> properties
    ) {
    }

    public record BlockState(
        String id,
        Map<String, String> properties
    ) {
    }

    public record BlockProperty(
        Type type,
        Set<String> values
    ) {
        public enum Type {
            INT,
            ENUM,
            DIRECTION,
            BOOLEAN,
        }
    }
}
