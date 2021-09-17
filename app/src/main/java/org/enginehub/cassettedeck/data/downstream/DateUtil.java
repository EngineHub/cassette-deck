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

package org.enginehub.cassettedeck.data.downstream;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtil {
    private static final DateTimeFormatter LEXICAL_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME.localizedBy(
        Locale.ROOT
    );

    /**
     * Encode the instant with a lexically sortable format.
     *
     * @param instant the instant
     * @return the date string
     */
    public static String formatLexicalInstant(Instant instant) {
        return LEXICAL_FORMATTER.format(instant.atZone(ZoneOffset.UTC));
    }

    public static Instant parseLexicalInstant(String value) {
        return LEXICAL_FORMATTER.parse(value, LocalDateTime::from).toInstant(ZoneOffset.UTC);
    }

    private DateUtil() {
    }
}
