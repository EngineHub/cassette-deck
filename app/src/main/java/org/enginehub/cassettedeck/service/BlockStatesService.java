package org.enginehub.cassettedeck.service;

import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface BlockStatesService {
    @Nullable BlockStates getBlockStates(int dataVersion) throws IOException;

    void setBlockStates(int dataVersion, BlockStates blockStates) throws IOException;
}
