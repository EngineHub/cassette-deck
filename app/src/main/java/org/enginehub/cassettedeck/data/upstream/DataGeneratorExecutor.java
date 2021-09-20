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
import org.enginehub.cassettedeck.jexec.InMemoryClassLoader;
import org.enginehub.cassettedeck.jexec.LibraryStorage;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class DataGeneratorExecutor {
    @Component
    public record Config(
        ObjectMapper mapper,
        LibraryStorage libraryStorage
    ) {
    }

    private final Semaphore generatorLimit = new Semaphore(1);
    private final Config config;
    private final MinecraftMetadata metadata;
    private final byte[] clientJar;

    public DataGeneratorExecutor(Config config, MinecraftMetadata metadata, byte[] clientJar) {
        this.config = config;
        this.metadata = metadata;
        this.clientJar = clientJar;
    }

    private InMemoryClassLoader prepareClassLoader() throws IOException {
        var jars = new ArrayList<byte[]>();
        jars.add(clientJar);
        for (MinecraftMetadata.Library library : metadata.libraries()) {
            jars.add(config.libraryStorage().getLibraryBytes(library.downloads().artifact()));
        }
        return new InMemoryClassLoader("data-gen", ClassLoader.getSystemClassLoader(), jars);
    }

    private void runGenerator(String... args) throws IOException {
        try (InMemoryClassLoader classLoader = prepareClassLoader()) {
            Class<?> mainClass;
            try {
                mainClass = Class.forName("net.minecraft.data.Main", true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Missing Data Generator Main class");
            }
            Method main;
            try {
                main = mainClass.getDeclaredMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Data Generator Main class did not have a main(String[])");
            }
            try {
                main.invoke(null, (Object) args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to invoke main(String[])", e);
            }
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
