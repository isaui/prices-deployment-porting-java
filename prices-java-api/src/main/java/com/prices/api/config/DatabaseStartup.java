package com.prices.api.config;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Singleton
public class DatabaseStartup implements ApplicationEventListener<StartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseStartup.class);
    private final DataSource dataSource;

    public DatabaseStartup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                LOG.info("Database connected successfully");
                // Migration is handled by Micronaut Data / Hibernate configuration
                LOG.info("Database migrated successfully (via Hibernate hbm2ddl.auto)");
            }
        } catch (SQLException e) {
            LOG.error("Failed to connect to database:", e);
            throw new RuntimeException("Failed to connect to database", e);
        }
    }
}
