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

import org.enginehub.cassettedeck.data.downstream.BlockStates;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.enginehub.cassettedeck.service.BlockStatesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/block-states")
public class BlockStatesController {
    private final BlockStatesService blockStatesService;

    public BlockStatesController(BlockStatesService blockStatesService) {
        this.blockStatesService = blockStatesService;
    }

    /**
     * Get the block states for a data version.
     *
     * @return the block states
     */
    @GetMapping("/{dataVersion}")
    public BlockStates getBlockStates(
        @PathVariable int dataVersion
    ) throws IOException {
        BlockStates states = blockStatesService.getBlockStates(dataVersion);
        if (states == null) {
            throw new NotFoundException("block-states");
        }
        return states;
    }
}
