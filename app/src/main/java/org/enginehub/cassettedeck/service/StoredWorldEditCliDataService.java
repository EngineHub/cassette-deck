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
import org.enginehub.cassettedeck.data.blob.BlobStorage;
import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.enginehub.cassettedeck.data.downstream.CliData;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class StoredWorldEditCliDataService implements WorldEditCliDataService {
    private final BlobStorage storage;
    private final ObjectMapper mapper;

    public StoredWorldEditCliDataService(
        @Qualifier("worldEditCliData") BlobStorage storage,
        ObjectMapper mapper
    ) {
        this.storage = storage;
        this.mapper = mapper;
    }

    @Override
    public @Nullable CliData getCliData(int dataVersion, int cliDataVersion) throws IOException {
        try (var input = storage.retrieve(dataVersion + "-" + cliDataVersion + ".json")) {
            if (input == null) {
                return null;
            }
            return mapper.readValue(input, CliData.class);
        }
    }

    @Override
    public void setCliData(int dataVersion, int cliDataVersion, CliData cliData) throws IOException {
        storage.store(dataVersion + "-" + cliDataVersion + ".json", stream -> mapper.writeValue(stream, cliData));
    }
}
