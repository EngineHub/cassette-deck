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

package org.enginehub.cassettedeck.data.upstream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.enginehub.cassettedeck.data.blob.LibraryStorage;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class DataGeneratorExecutor {
    private static final String JAVA_EXECUTABLE = ProcessHandle.current().info().command()
        .orElseThrow(() -> new IllegalStateException("Don't know the java executable for this process"));

    @Component
    public record Config(
        ObjectMapper mapper,
        LibraryStorage libraryStorage
    ) {
    }

    private final Semaphore generatorLimit = new Semaphore(1);
    private final Config config;
    private final MinecraftMetadata metadata;
    private final MinecraftMetadata.Download client;

    public DataGeneratorExecutor(Config config, MinecraftMetadata metadata, MinecraftMetadata.Download client) {
        this.config = config;
        this.metadata = metadata;
        this.client = client;
    }

    private List<Path> prepareClassPath() throws IOException {
        var jars = new ArrayList<Path>();
        jars.add(config.libraryStorage().getLibraryJar(client));
        for (MinecraftMetadata.Library library : metadata.libraries()) {
            jars.add(config.libraryStorage().getLibraryJar(library.downloads().artifact()));
        }
        return jars;
    }

    private void runGenerator(String... args) throws IOException {
        List<Path> classPath = prepareClassPath();
        Process process = new ProcessBuilder(
            new ImmutableList.Builder<String>()
                .add(JAVA_EXECUTABLE)
                .add("-Xms64M", "-Xmx4G")
                .add("-cp")
                .add(classPath.stream().map(String::valueOf).collect(Collectors.joining(File.pathSeparator)))
                .add("net.minecraft.data.Main")
                .add(args)
                .build()
        )
            .inheritIO()
            .start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Failed to run data gen, exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public MojangBlockStates generateBlockStates() throws IOException {
        generatorLimit.acquireUninterruptibly();
        Path tempFolder = Files.createTempDirectory("cassette-deck-blockstategen");
        tempFolder.toFile().deleteOnExit();
        try {
            runGenerator("--reports", "--output", tempFolder.toAbsolutePath().toString());
            return new MojangBlockStates(config.mapper().readValue(
                tempFolder.resolve("reports/blocks.json").toFile(),
                new TypeReference<>() {
                }
            ));
        } finally {
            generatorLimit.release();
            FileSystemUtils.deleteRecursively(tempFolder);
        }
    }
}
