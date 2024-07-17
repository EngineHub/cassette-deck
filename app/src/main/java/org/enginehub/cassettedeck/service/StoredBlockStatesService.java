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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enginehub.cassettedeck.data.blob.DiskStorage;
import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class StoredBlockStatesService implements BlockStatesService {
    private final DiskStorage storage;
    private final ObjectMapper mapper;

    public StoredBlockStatesService(
        @Qualifier("blockStateData") DiskStorage storage,
        ObjectMapper mapper
    ) {
        this.storage = storage;
        this.mapper = mapper;
    }

    @Override
    public @Nullable BlockStates getBlockStates(int dataVersion) throws IOException {
        try (var input = storage.retrieve(dataVersion + ".json")) {
            if (input == null) {
                return null;
            }
            return mapper.readValue(input, BlockStates.class);
        }
    }

    @Override
    public void setBlockStates(int dataVersion, BlockStates blockStates) throws IOException {
        storage.store(
            dataVersion + ".json",
            destination -> mapper.writeValue(destination.toFile(), blockStates)
        );
    }
}
