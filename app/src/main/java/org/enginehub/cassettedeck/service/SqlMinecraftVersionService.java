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
import org.enginehub.cassettedeck.data.upstream.DataVersionLoader;
import org.enginehub.cassettedeck.db.gen.Tables;
import org.enginehub.cassettedeck.db.gen.tables.daos.MinecraftVersionDao;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SqlMinecraftVersionService implements MinecraftVersionService {
    private final DSLContext dslContext;
    private final MinecraftVersionDao dao;
    private final DataVersionLoader versionLoader;

    public SqlMinecraftVersionService(
        DSLContext dslContext,
        MinecraftVersionDao dao,
        DataVersionLoader versionLoader
    ) {
        this.dslContext = dslContext;
        this.dao = dao;
        this.versionLoader = versionLoader;
    }

    @Override
    public Set<String> findMissingVersions(Set<String> knownVersions) {
        var missing = new HashSet<>(knownVersions);
        try (org.jooq.Cursor<Record1<String>> cursor = dslContext.select(Tables.MINECRAFT_VERSION.VERSION)
            .from(Tables.MINECRAFT_VERSION)
            .fetchLazy()) {
            for (Record1<String> record : cursor) {
                missing.remove(record.value1());
            }
        }
        return missing;
    }

    @Override
    public Cursor<String, Instant> getAllVersions(@Nullable Instant beforeDate, int limit) {
        Condition condition = DSL.trueCondition();
        if (beforeDate != null) {
            condition = condition.and(Tables.MINECRAFT_VERSION.RELEASE_DATE.lessThan(beforeDate));
        }
        var results = new ArrayList<String>(limit);
        Instant lastDate = null;
        try (var cursor = dslContext.select(Tables.MINECRAFT_VERSION.VERSION, Tables.MINECRAFT_VERSION.RELEASE_DATE)
            .from(Tables.MINECRAFT_VERSION)
            .where(condition)
            .orderBy(Tables.MINECRAFT_VERSION.RELEASE_DATE.desc())
            .limit(limit)
            .fetchLazy()) {
            for (var iterator = cursor.iterator(); iterator.hasNext(); ) {
                Record2<String, Instant> record = iterator.next();
                results.add(record.value1());
                if (!iterator.hasNext()) {
                    lastDate = record.value2();
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
        var result = this.dao.fetchOne(Tables.MINECRAFT_VERSION.VERSION, version);
        if (result == null) {
            throw new NotFoundException("minecraft-version");
        }
        if (result.getDataVersion() == null) {
            // We need to initialize this row.
            result.setDataVersion(
                versionLoader.load(result)
            );
            this.dslContext.update(Tables.MINECRAFT_VERSION)
                .set(Tables.MINECRAFT_VERSION.DATA_VERSION, result.getDataVersion())
                .where(Tables.MINECRAFT_VERSION.VERSION.eq(result.getVersion()))
                .execute();
        }
        return result;
    }
}
