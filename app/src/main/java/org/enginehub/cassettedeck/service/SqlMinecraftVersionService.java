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

package org.enginehub.cassettedeck.service;

import org.enginehub.cassettedeck.data.downstream.Cursor;
import org.enginehub.cassettedeck.db.gen.tables.daos.MinecraftVersionDao;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.db.gen.tables.records.MinecraftVersionRecord;
import org.jetbrains.annotations.Nullable;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static org.enginehub.cassettedeck.db.gen.Tables.MINECRAFT_VERSION;

@Service
public class SqlMinecraftVersionService implements MinecraftVersionService {
    private final DSLContext dslContext;
    private final MinecraftVersionDao dao;

    public SqlMinecraftVersionService(
        DSLContext dslContext,
        MinecraftVersionDao dao
    ) {
        this.dslContext = dslContext;
        this.dao = dao;
    }

    @Override
    public Set<String> findMissingVersions(Set<String> knownVersions) {
        var missing = new HashSet<>(knownVersions);
        try (org.jooq.Cursor<Record1<String>> cursor = dslContext.select(MINECRAFT_VERSION.VERSION)
            .from(MINECRAFT_VERSION)
            .fetchLazy()) {
            for (Record1<String> record : cursor) {
                missing.remove(record.value1());
            }
        }
        return missing;
    }

    @Override
    public Cursor<MinecraftVersionEntry, Instant> getAllVersions(@Nullable Instant beforeDate, int limit) {
        Condition condition = DSL.trueCondition();
        if (beforeDate != null) {
            condition = condition.and(MINECRAFT_VERSION.RELEASE_DATE.lessThan(beforeDate));
        }
        var results = new ArrayList<MinecraftVersionEntry>(limit);
        Instant lastDate = null;
        try (var cursor = dslContext.selectFrom(MINECRAFT_VERSION)
            .where(condition)
            .orderBy(MINECRAFT_VERSION.RELEASE_DATE.desc())
            .limit(limit)
            .fetchLazy()) {
            for (var iterator = cursor.iterator(); iterator.hasNext(); ) {
                MinecraftVersionRecord record = iterator.next();
                results.add(dao.mapper().map(record));
                if (!iterator.hasNext()) {
                    lastDate = record.getReleaseDate();
                }
            }
        }
        if (results.size() < limit) {
            // We ran out of results, there's no next page!
            lastDate = null;
        }
        return new Cursor<>(results, lastDate);
    }

    @Override
    public void insert(List<MinecraftVersionEntry> entries) {
        this.dao.insert(entries);
    }

    @Override
    public MinecraftVersionEntry getVersion(String version) {
        return this.dao.fetchOne(MINECRAFT_VERSION.VERSION, version);
    }

    @Override
    public Collection<MinecraftVersionEntry> findEntryByDataVersion(int dataVersion) {
        return dslContext.selectFrom(MINECRAFT_VERSION)
            .where(MINECRAFT_VERSION.DATA_VERSION.eq(dataVersion))
            .orderBy(MINECRAFT_VERSION.RELEASE_DATE.desc())
            .fetch(dao.mapper());
    }
}
