package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class PrepareExternalDatabaseStage implements PipelineStage {

    private final String externalDbHost;
    private final int externalDbPort;
    private final String pricesUser;      // Shared user for all projects
    private final String pricesPassword;  // Shared password for all projects

    public PrepareExternalDatabaseStage(
            String externalDbHost,
            int externalDbPort,
            String pricesUser,
            String pricesPassword) {
        this.externalDbHost = externalDbHost;
        this.externalDbPort = externalDbPort;
        this.pricesUser = pricesUser;
        this.pricesPassword = pricesPassword;
    }

    @Override
    public String name() {
        return "PrepareExternalDatabase";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        String slug = ctx.getProjectSlug();
        String dbName = NamingUtils.dbName(slug);

        // Always set DB context (deterministic: shared user + slug_db)
        ctx.setDbHost(externalDbHost);
        ctx.setDbPort(externalDbPort);
        ctx.setDbName(dbName);
        ctx.setDbUsername(pricesUser);
        ctx.setDbPassword(pricesPassword);

        ctx.addLog(String.format("Connecting to external PostgreSQL at %s:%d", externalDbHost, externalDbPort));

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres", externalDbHost, externalDbPort);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, pricesUser, pricesPassword)) {
            ctx.addLog("Connected to PostgreSQL as prices user");

            // Check if database already exists
            if (databaseExists(conn, dbName)) {
                ctx.addLog(String.format("Database '%s' already exists", dbName));
                return;
            }

            // Create database (no user creation - using shared prices user)
            createDatabase(conn, dbName);
            ctx.addLog(String.format("Created database: %s", dbName));

            ctx.addLog("External database setup completed successfully");

        } catch (SQLException e) {
            ctx.addLog(String.format("Database setup failed: %s", e.getMessage()));
            throw new Exception("Failed to setup external database", e);
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Never drop DB on redeploy - it contains user data
        if (ctx.isRedeploy()) {
            ctx.addLog("Redeploy detected, skipping database rollback (preserving existing data)");
            return;
        }

        if (ctx.getDbName() == null) {
            ctx.addLog("No database to rollback");
            return;
        }

        ctx.addLog("Rolling back database setup (first deployment failed)...");
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres", externalDbHost, externalDbPort);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, pricesUser, pricesPassword)) {
            dropDatabase(conn, ctx.getDbName());
            ctx.addLog(String.format("Dropped database: %s", ctx.getDbName()));
        } catch (SQLException e) {
            ctx.addLog(String.format("Rollback failed: %s", e.getMessage()));
            log.error("Failed to rollback database for project: {}", ctx.getProjectSlug(), e);
        }
    }

    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        String query = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void createDatabase(Connection conn, String dbName) throws SQLException {
        String sql = String.format("CREATE DATABASE \"%s\"", dbName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void dropDatabase(Connection conn, String dbName) throws SQLException {
        // Terminate existing connections first
        String terminateSql = String.format(
                "SELECT pg_terminate_backend(pg_stat_activity.pid) " +
                        "FROM pg_stat_activity " +
                        "WHERE pg_stat_activity.datname = '%s' " +
                        "AND pid <> pg_backend_pid()",
                dbName.replace("'", "''"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(terminateSql);
        }

        // Drop database
        String dropSql = String.format("DROP DATABASE IF EXISTS \"%s\"", dbName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(dropSql);
        }
    }
}
