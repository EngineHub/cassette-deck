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

package org.enginehub.cassettedeck.jexec;

import com.google.common.collect.Iterators;
import com.google.common.io.Closer;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Enumeration;
import java.util.List;

public class InMemoryClassLoader extends ClassLoader implements AutoCloseable {
    private static final String PROTOCOL = "x-cassette-deck-in-memory";

    private class InMemoryURLHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL u) {
            if (!PROTOCOL.equals(u.getProtocol())) {
                throw new IllegalArgumentException("This only handles " + PROTOCOL);
            }
            String name = u.getPath();
            ZipFile file = jars.stream().filter(zf -> zf.getEntry(name) != null).findFirst().orElse(null);
            ZipArchiveEntry entry = file == null ? null : file.getEntry(name);
            return new InMemoryURLConnection(u, file, entry);
        }
    }

    private static class InMemoryURLConnection extends URLConnection {
        private final ZipFile file;
        private final ZipArchiveEntry entry;
        private InputStream stream;

        private InMemoryURLConnection(URL url, ZipFile file, ZipArchiveEntry entry) {
            super(url);
            this.file = file;
            this.entry = entry;
        }

        @Override
        public void connect() throws IOException {
            if (!connected) {
                if (file == null || entry == null) {
                    throw new FileNotFoundException(getURL().getPath());
                }
                stream = file.getInputStream(entry);
                connected = true;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return stream;
        }

        @Override
        public long getContentLengthLong() {
            return entry.getSize();
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }
    }

    private final InMemoryURLHandler handler = new InMemoryURLHandler();
    private final Closer closer = Closer.create();
    private final List<ZipFile> jars;

    public InMemoryClassLoader(String name, ClassLoader parent, List<byte[]> jars) {
        super(name, parent);
        this.jars = jars.stream()
            .map(b -> {
                try {
                    return closer.register(new ZipFile(new SeekableInMemoryByteChannel(b)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .toList();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        byte[] b;
        try {
            b = getBytes(path);
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to load " + name, e);
        }
        if (b != null) {
            return defineClass(name, b, 0, b.length);
        }
        return super.findClass(name);
    }

    @Override
    protected URL findResource(String name) {
        try {
            var iter = findResources(name).asIterator();
            return iter.hasNext() ? iter.next() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return Iterators.asEnumeration(
            jars.stream()
                .filter(zf -> zf.getEntry(name) != null)
                .map(zf -> {
                    try {
                        return new URL(PROTOCOL, null, 0, name, handler);
                    } catch (MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .iterator()
        );
    }

    private byte @Nullable [] getBytes(String path) throws IOException {
        for (ZipFile zf : jars) {
            ZipArchiveEntry entry = zf.getEntry(path);
            if (entry != null) {
                try (InputStream inputStream = zf.getInputStream(entry)) {
                    return inputStream.readAllBytes();
                }
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        closer.close();
    }
}
