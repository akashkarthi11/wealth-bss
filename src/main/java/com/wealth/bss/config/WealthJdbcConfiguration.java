package com.wealth.bss.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// The datasource is built by PostgresEndpoint (the pool, and the swap an admin can make from Settings > Environment); the pool settings are the same
// as before: wealth.jdbc.url, username, password, pool-size and connection-timeout-ms. Neon's serverless Postgres auto-suspends when idle, so the
// first connection after a suspend can take longer than a typical timeout.
@Configuration
@ConditionalOnProperty(name = "wealth.jdbc.url")
public class WealthJdbcConfiguration {
    @Bean
    public DataSource wealthDataSource(PostgresEndpoint postgres) {
        return postgres.dataSource();
    }

    @Bean
    public JdbcTemplate wealthJdbcTemplate(DataSource wealthDataSource) {
        return new JdbcTemplate(wealthDataSource);
    }
}
