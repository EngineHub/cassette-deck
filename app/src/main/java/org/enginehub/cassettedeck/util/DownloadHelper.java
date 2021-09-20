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

package org.enginehub.cassettedeck.util;

import com.google.common.hash.Hashing;
import org.enginehub.cassettedeck.data.upstream.MinecraftMetadata;
import org.enginehub.cassettedeck.exception.DownloadException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

public class DownloadHelper {
    public static byte[] downloadVerifiedBytes(RestTemplate restTemplate, MinecraftMetadata.Download download)
        throws DownloadException {
        byte[] bytes = restTemplate.getForObject(download.url(), byte[].class);
        Objects.requireNonNull(bytes, "bytes is null");
        if (bytes.length != download.size()) {
            throw new DownloadException(
                DownloadException.Kind.LENGTH_MISMATCH,
                new AssertionError(bytes.length + " != " + download.size())
            );
        }
        // Mojang uses sha1, we have to as well
        @SuppressWarnings("deprecation")
        var localSha1 = Hashing.sha1().hashBytes(bytes).toString();
        if (!localSha1.equals(download.sha1())) {
            throw new DownloadException(
                DownloadException.Kind.HASH_MISMATCH,
                new AssertionError(localSha1 + " != " + download.sha1())
            );
        }
        return bytes;
    }
}
