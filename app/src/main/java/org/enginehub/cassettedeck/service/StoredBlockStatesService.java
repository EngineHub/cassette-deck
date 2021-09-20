package org.enginehub.cassettedeck.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enginehub.cassettedeck.data.blob.BlobStorage;
import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class StoredBlockStatesService implements BlockStatesService {
    private final BlobStorage storage;
    private final ObjectMapper mapper;

    public StoredBlockStatesService(
        @Qualifier("blockStateData") BlobStorage storage,
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
        storage.store(dataVersion + ".json", stream -> mapper.writeValue(stream, blockStates));
    }
}
