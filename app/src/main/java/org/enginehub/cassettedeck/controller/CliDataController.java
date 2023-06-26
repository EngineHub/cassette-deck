package org.enginehub.cassettedeck.controller;

import org.enginehub.cassettedeck.data.downstream.CliData;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.enginehub.cassettedeck.service.WorldEditCliDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/we-cli-data")
public class CliDataController {
    private final WorldEditCliDataService worldEditCliDataService;

    public CliDataController(WorldEditCliDataService worldEditCliDataService) {
        this.worldEditCliDataService = worldEditCliDataService;
    }

    /**
     * Get the block states for a data version.
     *
     * @return the block states
     */
    @GetMapping("/{dataVersion}/{cliDataVersion}")
    public CliData getWeCliData(
        @PathVariable int dataVersion,
        @PathVariable int cliDataVersion
    ) throws IOException {
        CliData cliData = worldEditCliDataService.getCliData(dataVersion, cliDataVersion);
        if (cliData == null) {
            throw new NotFoundException("we-cli-data");
        }
        return cliData;
    }
}
