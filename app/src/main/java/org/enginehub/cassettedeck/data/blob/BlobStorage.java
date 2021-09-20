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

package org.enginehub.cassettedeck.data.blob;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface BlobStorage {
    @FunctionalInterface
    interface OutputStreamConsumer {
        void accept(OutputStream stream) throws IOException;
    }

    /**
     * If there is no blob for the given key, use {@code consumer} to fill it, then return a stream to get the contents
     * of the blob.
     *
     * @param key the key
     * @param consumer the blob provider
     * @return the content of the blob
     * @throws IOException if there is an I/O error
     */
    InputStream storeIfAbsent(String key, OutputStreamConsumer consumer) throws IOException;

    @Nullable InputStream retrieve(String key) throws IOException;

    void store(String key, OutputStreamConsumer consumer) throws IOException;
}
