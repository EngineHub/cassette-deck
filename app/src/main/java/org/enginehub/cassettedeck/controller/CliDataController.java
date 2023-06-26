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

package org.enginehub.cassettedeck.controller;

import org.enginehub.cassettedeck.data.downstream.CliData;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.enginehub.cassettedeck.service.WorldEditCliDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Get the WorldEdit CLI data for a data version.
     *
     * @return the WE CLI data
     */
    @GetMapping("/{dataVersion}")
    public CliData getWeCliData(
        @PathVariable int dataVersion,
        @RequestParam(defaultValue = "1") int cliDataVersion
    ) throws IOException {
        CliData cliData = worldEditCliDataService.getCliData(dataVersion, cliDataVersion);
        if (cliData == null) {
            throw new NotFoundException("we-cli-data");
        }
        return cliData;
    }

    /**
     * Uploads a WE CLI Data file for a given data version and CLI data version.
     */
    @PutMapping("/{dataVersion}/{cliDataVersion}")
    public void putWeCliData(
        @PathVariable int dataVersion,
        @PathVariable int cliDataVersion,
        @RequestBody CliData cliData
    ) throws IOException {
        worldEditCliDataService.setCliData(dataVersion, cliDataVersion, cliData);
    }
}
