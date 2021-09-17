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
