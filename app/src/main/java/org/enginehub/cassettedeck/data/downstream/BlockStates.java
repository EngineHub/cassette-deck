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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
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
            ;

            @JsonValue
            public String jacksonName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }
}
