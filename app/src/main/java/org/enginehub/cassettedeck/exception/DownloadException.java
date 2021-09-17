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

package org.enginehub.cassettedeck.exception;

public class DownloadException extends RuntimeException {
    public enum Kind {
        IO_ERROR("I/O error occurred", "io"),
        LENGTH_MISMATCH("Length did not match", "length-mismatch"),
        HASH_MISMATCH("Hash did not match", "hash-mismatch"),
        ;

        public final String message;
        private final String codePrefix;

        Kind(String message, String codePrefix) {
            this.message = message;
            this.codePrefix = codePrefix;
        }

        public String code() {
            return codePrefix + ".download.error";
        }
    }

    private final Kind kind;

    public DownloadException(Kind kind) {
        super(kind.message);
        this.kind = kind;
    }

    public DownloadException(Kind kind, Throwable cause) {
        super(kind.message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
