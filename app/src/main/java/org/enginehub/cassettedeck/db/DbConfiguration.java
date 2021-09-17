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

package org.enginehub.cassettedeck.db;

import org.enginehub.cassettedeck.db.gen.tables.daos.AuthorizedTokenDao;
import org.enginehub.cassettedeck.db.gen.tables.daos.MinecraftVersionDao;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
public class DbConfiguration {
    @Bean
    public Connection connection(@Value("${database.url}") String databaseUrl) throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }

    @Bean
    public org.jooq.Configuration configuration(Connection connection) {
        return new DefaultConfiguration().set(connection).set(SQLDialect.SQLITE);
    }

    @Bean
    public DSLContext dslContext(org.jooq.Configuration configuration) {
        return DSL.using(configuration);
    }

    @Bean
    public MinecraftVersionDao minecraftVersionDao(org.jooq.Configuration configuration) {
        return new MinecraftVersionDao(configuration);
    }

    @Bean
    public AuthorizedTokenDao authorizedTokenDao(org.jooq.Configuration configuration) {
        return new AuthorizedTokenDao(configuration);
    }

}
