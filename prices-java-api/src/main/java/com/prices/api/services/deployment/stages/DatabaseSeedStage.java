package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class DatabaseSeedStage implements PipelineStage {

    @Override
    public String name() {
        return "DatabaseSeed";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        // Skip if redeploy - DB already seeded from previous deployment
        if (ctx.isRedeploy()) {
            ctx.addLog("Redeploy detected, skipping database seeding (data already exists)");
            return;
        }

        if (ctx.getBackendDistPath() == null) {
            ctx.addLog("No backend found, skipping database seeding");
            return;
        }

        List<Path> seedFiles = findSeedFiles(ctx.getBackendDistPath());
        if (seedFiles.isEmpty()) {
            ctx.addLog("No seed files found, skipping database seeding");
            return;
        }

        ctx.addLog(String.format("Found %d seed file(s): %s", seedFiles.size(), 
            seedFiles.stream().map(p -> p.getFileName().toString()).toList()));

        waitForBackendReady(ctx);

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                ctx.getDbHost(), ctx.getDbPort(), ctx.getDbName());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, ctx.getDbUsername(), ctx.getDbPassword())) {
            ctx.addLog("Connected to database for seeding");

            for (Path seedFile : seedFiles) {
                ctx.addLog(String.format("Executing seed file: %s", seedFile.getFileName()));
                executeSqlFile(conn, seedFile);
                ctx.addLog(String.format("Successfully executed: %s", seedFile.getFileName()));
            }

            ctx.addLog("Database seeding completed successfully");

        } catch (SQLException e) {
            ctx.addLog(String.format("Database seeding failed: %s", e.getMessage()));
            throw new Exception("Failed to seed database", e);
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        ctx.addLog("Database seeding rollback not supported (data already inserted)");
    }

    private List<Path> findSeedFiles(Path backendDistPath) throws IOException {
        List<Path> seedFiles = new ArrayList<>();

        Path seedSql = backendDistPath.resolve("seed.sql");
        if (Files.exists(seedSql) && Files.isRegularFile(seedSql)) {
            seedFiles.add(seedSql);
        }

        Path seedDir = backendDistPath.resolve("seed");
        if (Files.exists(seedDir) && Files.isDirectory(seedDir)) {
            try (Stream<Path> files = Files.list(seedDir)) {
                files.filter(p -> p.toString().endsWith(".sql"))
                     .sorted()
                     .forEach(seedFiles::add);
            }
        }

        Path sqlDir = backendDistPath.resolve("sql");
        if (Files.exists(sqlDir) && Files.isDirectory(sqlDir)) {
            try (Stream<Path> files = Files.list(sqlDir)) {
                files.filter(p -> p.toString().endsWith(".sql"))
                     .sorted()
                     .forEach(seedFiles::add);
            }
        }

        return seedFiles;
    }

    private void waitForBackendReady(DeploymentContext ctx) throws InterruptedException {
        ctx.addLog("Waiting for backend migration to complete (checking for tables)...");
        int maxRetries = 30;
        int retryDelayMs = 2000;

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                ctx.getDbHost(), ctx.getDbPort(), ctx.getDbName());

        for (int i = 0; i < maxRetries; i++) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, ctx.getDbUsername(), ctx.getDbPassword())) {
                if (hasTablesInDatabase(conn)) {
                    ctx.addLog("Migration completed, tables found in database");
                    return;
                }
                ctx.addLog(String.format("No tables yet, waiting... (%d/%d)", i + 1, maxRetries));
            } catch (SQLException e) {
                ctx.addLog(String.format("Connection failed, retrying... (%d/%d)", i + 1, maxRetries));
            }

            if (i < maxRetries - 1) {
                Thread.sleep(retryDelayMs);
            }
        }

        throw new RuntimeException("Migration not completed after " + maxRetries + " retries (no tables found)");
    }

    private boolean hasTablesInDatabase(Connection conn) throws SQLException {
        String query = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'";
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    private void executeSqlFile(Connection conn, Path sqlFile) throws IOException, SQLException {
        String content = Files.readString(sqlFile);
        
        String[] statements = content.split(";");
        
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }
        }
    }
}
