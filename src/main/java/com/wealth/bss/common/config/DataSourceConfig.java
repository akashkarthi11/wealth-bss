package com.wealth.bss.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;

// Dormant unless spring.datasource.url is deliberately set - wealth-bss's own WealthJdbcConfiguration
// (wealth.jdbc.url, pooled Hikari) is the DataSource actually in use; this one only activates if
// something is explicitly pointed at a second, separate database via spring.datasource.*.
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class DataSourceConfig {

    private static final KarthiLogger LOGGER =
            new KarthiLogger(DataSourceConfig.class);

    private final String url;
    private final String username;
    private final String encryptedPassword;

    public DataSourceConfig(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String encryptedPassword) {

        this.url = url;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
    }

    @Bean
    public DataSource dataSource() {
        try {
            LOGGER.info("Initializing DataSource configuration");

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(url);
            dataSource.setUsername(username);

//            String password = encryptionUtil.decrypt(encryptedPassword);
            dataSource.setPassword(encryptedPassword);

            LOGGER.info("DataSource configured successfully");
            return dataSource;

        } catch (IllegalArgumentException ex) {
            LOGGER.error("Invalid datasource configuration", ex);
            throw new KarthiException(BaseError.INVALID_DATASOURCE_CONFIG, ex);

        } catch (Exception ex) {
            // encryption or unexpected failure
            LOGGER.error("Failed to initialize datasource", ex);
            throw new KarthiException(BaseError.DATABASE_CONNECTION_FAILED, ex);
        }
    }
}
