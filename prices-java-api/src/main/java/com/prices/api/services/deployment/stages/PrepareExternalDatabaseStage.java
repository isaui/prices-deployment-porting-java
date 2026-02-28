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
    private final String deployerUser;
    private final String deployerPassword;

    public PrepareExternalDatabaseStage(
            String externalDbHost,
            int externalDbPort,
            String deployerUser,
            String deployerPassword) {
        this.externalDbHost = externalDbHost;
        this.externalDbPort = externalDbPort;
        this.deployerUser = deployerUser;
        this.deployerPassword = deployerPassword;
    }

    @Override
    public String name() {
        return "PrepareExternalDatabase";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        // Skip if redeploy - DB already exists from previous successful deployment
        if (ctx.isRedeploy()) {
            ctx.addLog("Redeploy detected, skipping database setup (using existing credentials)");
            return;
        }

        String slug = ctx.getProjectSlug();
        String dbName = NamingUtils.dbName(slug);
        String dbUser = NamingUtils.dbUser(slug);

        ctx.addLog(String.format("Connecting to external PostgreSQL at %s:%d", externalDbHost, externalDbPort));

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres", externalDbHost, externalDbPort);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, deployerUser, deployerPassword)) {
            ctx.addLog("Connected to PostgreSQL as deployment service");

            // Check if database already exists - if so, skip entirely (use existing env vars)
            if (databaseExists(conn, dbName)) {
                ctx.addLog(String.format("Database '%s' already exists, using existing credentials", dbName));
                return;
            }

            // First deployment - create DB, user, and set credentials
            String dbPassword = NamingUtils.generateSecurePassword(16);

            createDatabase(conn, dbName);
            ctx.addLog(String.format("Created database: %s", dbName));

            createUser(conn, dbUser, dbPassword);
            ctx.addLog(String.format("Created user: %s", dbUser));

            grantPrivileges(conn, dbName, dbUser);
            ctx.addLog(String.format("Granted privileges on database '%s' to user '%s'", dbName, dbUser));

            // Store in context for first deployment
            ctx.setDbHost(externalDbHost);
            ctx.setDbPort(externalDbPort);
            ctx.setDbName(dbName);
            ctx.setDbUsername(dbUser);
            ctx.setDbPassword(dbPassword);

            ctx.addLog("External database setup completed successfully");

        } catch (SQLException e) {
            ctx.addLog(String.format("Database setup failed: %s", e.getMessage()));
            throw new Exception("Failed to setup external database", e);
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Never drop DB on redeploy - it contains user data from previous deployments
        if (ctx.isRedeploy()) {
            ctx.addLog("Redeploy detected, skipping database rollback (preserving existing data)");
            return;
        }

        if (ctx.getDbName() == null || ctx.getDbUsername() == null) {
            ctx.addLog("No database to rollback");
            return;
        }

        ctx.addLog("Rolling back database setup (first deployment failed)...");
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres", externalDbHost, externalDbPort);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, deployerUser, deployerPassword)) {
            // Drop database
            dropDatabase(conn, ctx.getDbName());
            ctx.addLog(String.format("Dropped database: %s", ctx.getDbName()));

            // Drop user
            dropUser(conn, ctx.getDbUsername());
            ctx.addLog(String.format("Dropped user: %s", ctx.getDbUsername()));

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

    private boolean userExists(Connection conn, String username) throws SQLException {
        String query = "SELECT 1 FROM pg_roles WHERE rolname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
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

    private void createUser(Connection conn, String username, String password) throws SQLException {
        String sql = String.format("CREATE USER \"%s\" WITH PASSWORD '%s'", username, password.replace("'", "''"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void alterUserPassword(Connection conn, String username, String password) throws SQLException {
        String sql = String.format("ALTER USER \"%s\" WITH PASSWORD '%s'", username, password.replace("'", "''"));
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void grantPrivileges(Connection conn, String dbName, String username) throws SQLException {
        String sql = String.format("GRANT ALL PRIVILEGES ON DATABASE \"%s\" TO \"%s\"", dbName, username);
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

    private void dropUser(Connection conn, String username) throws SQLException {
        String sql = String.format("DROP USER IF EXISTS \"%s\"", username);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
