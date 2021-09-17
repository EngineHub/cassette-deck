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

import com.google.common.base.Throwables;
import org.enginehub.cassettedeck.data.downstream.Cursor;
import org.enginehub.cassettedeck.data.upstream.DataVersionLoader;
import org.enginehub.cassettedeck.db.gen.tables.daos.MinecraftVersionDao;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
import org.jetbrains.annotations.Nullable;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static org.enginehub.cassettedeck.db.gen.Tables.MINECRAFT_VERSION;

@Service
public class SqlMinecraftVersionService implements MinecraftVersionService {
    private final Map<String, CompletableFuture<Integer>> dataVersionLoads = new ConcurrentHashMap<>();
    private final DSLContext dslContext;
    private final MinecraftVersionDao dao;
    private final DataVersionLoader versionLoader;
    private final Executor workExecutor;

    public SqlMinecraftVersionService(
        DSLContext dslContext,
        MinecraftVersionDao dao,
        DataVersionLoader versionLoader,
        @Qualifier("applicationTaskExecutor") Executor workExecutor
    ) {
        this.dslContext = dslContext;
        this.dao = dao;
        this.versionLoader = versionLoader;
        this.workExecutor = workExecutor;
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
    public Cursor<String, Instant> getAllVersions(@Nullable Instant beforeDate, int limit) {
        Condition condition = DSL.trueCondition();
        if (beforeDate != null) {
            condition = condition.and(MINECRAFT_VERSION.RELEASE_DATE.lessThan(beforeDate));
        }
        var results = new ArrayList<String>(limit);
        Instant lastDate = null;
        try (var cursor = dslContext.select(MINECRAFT_VERSION.VERSION, MINECRAFT_VERSION.RELEASE_DATE)
            .from(MINECRAFT_VERSION)
            .where(condition)
            .orderBy(MINECRAFT_VERSION.RELEASE_DATE.desc())
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
        var result = this.dao.fetchOne(MINECRAFT_VERSION.VERSION, version);
        if (result == null) {
            return null;
        }
        initializeDataVersionIfNeeded(result);
        return result;
    }

    @Override
    public Collection<MinecraftVersionEntry> findEntryByDataVersion(int dataVersion) {
        var result = dslContext.selectFrom(MINECRAFT_VERSION)
            .where(MINECRAFT_VERSION.DATA_VERSION.eq(dataVersion))
            .orderBy(MINECRAFT_VERSION.RELEASE_DATE.desc())
            .fetch(dao.mapper());
        if (!result.isEmpty()) {
            return result;
        }
        // It might be valid, but un-loaded.
        // Load all uninitialized entries from newest to oldest to find it!
        return findInUninitializedEntries(dataVersion);
    }

    private Collection<MinecraftVersionEntry> findInUninitializedEntries(int dataVersion) {
        var result = new ArrayList<MinecraftVersionEntry>();
        try (var cursor = dslContext.selectFrom(MINECRAFT_VERSION)
            .where(MINECRAFT_VERSION.DATA_VERSION.isNull())
            .orderBy(MINECRAFT_VERSION.RELEASE_DATE.desc())
            .fetchLazy()) {
            while (cursor.hasNext()) {
                var nextSet = cursor.fetchNext(8);
                List<CompletableFuture<MinecraftVersionEntry>> futures = nextSet.stream()
                    .map(record -> {
                        var entry = dao.mapper().map(record);
                        Objects.requireNonNull(entry, () -> "failed to map record for " + record.getVersion());
                        return entry;
                    })
                    .map(e -> CompletableFuture.supplyAsync(() -> {
                        initializeDataVersionIfNeeded(e);
                        return e;
                    }, workExecutor))
                    .toList();
                try {
                    CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).join();
                } catch (CompletionException e) {
                    var cause = e.getCause();
                    Throwables.throwIfUnchecked(cause);
                    throw new RuntimeException(cause);
                }
                for (var future : futures) {
                    var joined = future.join();
                    if (joined.getDataVersion() == dataVersion) {
                        result.add(joined);
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        // We've initialized everything and didn't find it.
        return List.of();
    }

    private void initializeDataVersionIfNeeded(MinecraftVersionEntry result) {
        if (result.getDataVersion() != null) {
            return;
        }
        var newFuture = new CompletableFuture<Integer>();
        var future = dataVersionLoads.computeIfAbsent(result.getVersion(), __ -> newFuture);
        int dataVersion;
        if (future != newFuture) {
            dataVersion = future.join();
        } else {
            try {
                dataVersion = versionLoader.load(result);
                future.complete(dataVersion);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                dataVersionLoads.remove(result.getVersion());
                throw t;
            }
        }
        result.setDataVersion(dataVersion);
        this.dslContext.update(MINECRAFT_VERSION)
            .set(MINECRAFT_VERSION.DATA_VERSION, result.getDataVersion())
            .where(MINECRAFT_VERSION.VERSION.eq(result.getVersion()))
            .execute();
        dataVersionLoads.remove(result.getVersion());
    }
}
