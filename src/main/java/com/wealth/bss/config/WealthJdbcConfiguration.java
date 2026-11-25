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
@ConditionalOnProperty(name = "wealth.jdbc.url")
public class WealthJdbcConfiguration {
    @Bean
    public DataSource wealthDataSource(
            @Value("${wealth.jdbc.url}") String configuredJdbcUrl,
            @Value("${wealth.jdbc.username:}") String username,
            @Value("${wealth.jdbc.password:}") String password,
            @Value("${wealth.jdbc.pool-size:5}") int poolSize) {
        String jdbcUrl = configuredJdbcUrl.startsWith("postgresql://")
                ? "jdbc:" + configuredJdbcUrl
                : configuredJdbcUrl;

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
