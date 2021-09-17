package org.enginehub.cassettedeck.service;

import org.enginehub.cassettedeck.data.downstream.Cursor;
import org.enginehub.cassettedeck.db.gen.Tables;
import org.enginehub.cassettedeck.db.gen.tables.daos.MinecraftVersionDao;
import org.enginehub.cassettedeck.db.gen.tables.pojos.MinecraftVersionEntry;
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
import java.util.Set;

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
    public void insert(String version, Instant releaseInstant) {
        this.dao.insert(new MinecraftVersionEntry(version, null, releaseInstant));
    }
}
