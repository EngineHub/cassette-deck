package org.enginehub.cassettedeck.service;

import org.enginehub.cassettedeck.data.downstream.CliData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface WorldEditCliDataService {

    @Nullable CliData getCliData(int dataVersion, int cliDataVersion) throws IOException;

    void setCliData(int dataVersion, int cliDataVersion, CliData cliData) throws IOException;

}
