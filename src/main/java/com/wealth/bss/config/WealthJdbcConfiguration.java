package com.wealth.bss.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "WEALTH_JDBC_URL")
public class WealthJdbcConfiguration {
    @Bean
    public DataSource wealthDataSource(
            @Value("${WEALTH_JDBC_URL}") String jdbcUrl,
            @Value("${WEALTH_DB_USERNAME:}") String username,
            @Value("${WEALTH_DB_PASSWORD:}") String password,
            @Value("${WEALTH_DB_POOL_SIZE:5}") int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        if (!username.isBlank()) config.setUsername(username);
        if (!password.isBlank()) config.setPassword(password);
        config.setMaximumPoolSize(Math.max(1, Math.min(poolSize, 10)));
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("wealth-bss-postgres");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate wealthJdbcTemplate(DataSource wealthDataSource) {
        return new JdbcTemplate(wealthDataSource);
    }
}
