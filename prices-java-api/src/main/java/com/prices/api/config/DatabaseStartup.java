package com.prices.api.config;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Singleton
public class DatabaseStartup implements ApplicationEventListener<StartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseStartup.class);
    private final DataSource dataSource;
    private final DatabaseConfig databaseConfig;

    public DatabaseStartup(DataSource dataSource, DatabaseConfig databaseConfig) {
        this.dataSource = dataSource;
        this.databaseConfig = databaseConfig;
    }

    @Override
    @Transactional
    public void onApplicationEvent(StartupEvent event) {
        // Check main database
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                LOG.info("Main database connected successfully");
                LOG.info("Database migrated successfully (via Hibernate hbm2ddl.auto)");
            }
        } catch (SQLException e) {
            LOG.error("Failed to connect to main database:", e);
            throw new RuntimeException("Failed to connect to main database", e);
        }

        // Check external database for deployments
        checkExternalDatabase();
    }

    private void checkExternalDatabase() {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres",
                databaseConfig.getHost(), databaseConfig.getPort());

        LOG.info("Checking external database connectivity at {}:{}", 
                databaseConfig.getHost(), databaseConfig.getPort());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, 
                databaseConfig.getDeployerUser(), databaseConfig.getDeployerPassword())) {
            if (conn.isValid(5)) {
                LOG.info("External database connected successfully (user: {})", 
                        databaseConfig.getDeployerUser());
            }
        } catch (SQLException e) {
            LOG.error("Failed to connect to external database at {}:{} with user '{}': {}", 
                    databaseConfig.getHost(), databaseConfig.getPort(),
                    databaseConfig.getDeployerUser(), e.getMessage());
            throw new RuntimeException("Failed to connect to external database. " +
                    "Check DB_DEPLOYER_HOST, DB_DEPLOYER_PORT, DB_DEPLOYER_USER, DB_DEPLOYER_PASSWORD", e);
        }
    }
}
