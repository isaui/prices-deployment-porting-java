package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class CleanupStage implements PipelineStage {

    private final String[] dockerComposeCmd;
    private final String externalDbHost;
    private final int externalDbPort;
    private final String pricesUser;
    private final String pricesPassword;

    public CleanupStage(String[] dockerComposeCmd, String externalDbHost, int externalDbPort,
                        String pricesUser, String pricesPassword) {
        this.dockerComposeCmd = dockerComposeCmd;
        this.externalDbHost = externalDbHost;
        this.externalDbPort = externalDbPort;
        this.pricesUser = pricesUser;
        this.pricesPassword = pricesPassword;
    }

    @Override
    public String name() {
        return "Cleanup Resources";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        String projectName = NamingUtils.projectName(ctx.getProjectSlug());
        ctx.addLog("Starting cleanup for project: " + projectName);

        // 1. Stop and remove containers + volumes
        Path composePath = ctx.getComposePath();
        if (composePath != null && Files.exists(composePath)) {
            ctx.addLog("Stopping containers using docker-compose...");
            // docker-compose -f <path> -p <name> down -v --rmi local
            List<String> args = new ArrayList<>();
            args.add("-f");
            args.add(composePath.toString());
            args.add("-p");
            args.add(projectName);
            args.add("down");
            args.add("-v");
            args.add("--rmi");
            args.add("local");

            try {
                executeDockerCompose(ctx, args);
                ctx.addLog("Containers and volumes removed");
            } catch (Exception e) {
                ctx.addLog("Warning: docker-compose down failed: " + e.getMessage());
            }
        } else {
            // Try to stop by project name filter if compose file missing
            // This part is harder to port 1:1 without shell piping easily,
            // but we can just rely on the fact that if compose is missing, maybe we just
            // clean up files.
            // Or we can try `docker ps -aq --filter name=...`
            ctx.addLog("No compose file found, attempting cleanup by container name...");
            cleanupByName(ctx, projectName);
        }

        // 2. Remove Docker images (explicitly if not covered by --rmi local)
        // Go code does explicit removal too.
        String frontendImage = NamingUtils.containerName("frontend", ctx.getProjectSlug()) + ":latest";
        String backendImage = NamingUtils.containerName("backend", ctx.getProjectSlug()) + ":latest";

        removeImage(ctx, frontendImage);
        removeImage(ctx, backendImage);

        // 3. Drop database and user
        cleanupDatabase(ctx);

        // 4. Remove extracted files
        if (ctx.getExtractedPath() != null && Files.exists(ctx.getExtractedPath())) {
            try {
                deleteDirectory(ctx.getExtractedPath());
                ctx.addLog("Removed deployment files: " + ctx.getExtractedPath());
            } catch (IOException e) {
                ctx.addLog("Warning: failed to remove deployment files: " + e.getMessage());
            }
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // No rollback for cleanup
        ctx.addLog("Cleanup rollback: no action");
    }

    private void executeDockerCompose(DeploymentContext ctx, List<String> args) throws Exception {
        List<String> fullCmd = new ArrayList<>();
        for (String s : dockerComposeCmd)
            fullCmd.add(s);
        fullCmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ctx.addLog(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Exit code " + exitCode);
        }
    }

    private void cleanupByName(DeploymentContext ctx, String projectName) {
        try {
            // docker ps -aq --filter name=projectName
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-aq", "--filter", "name=" + projectName);
            Process p = pb.start();
            List<String> ids = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty())
                        ids.add(line.trim());
                }
            }
            p.waitFor();

            for (String id : ids) {
                ctx.addLog("Stopping/Removing container: " + id);
                new ProcessBuilder("docker", "stop", id).start().waitFor();
                new ProcessBuilder("docker", "rm", "-v", id).start().waitFor();
            }
        } catch (Exception e) {
            ctx.addLog("Warning: cleanup by name failed: " + e.getMessage());
        }
    }

    private void removeImage(DeploymentContext ctx, String imageName) {
        try {
            ctx.addLog("Removing image: " + imageName);
            new ProcessBuilder("docker", "rmi", "-f", imageName).start().waitFor();
        } catch (Exception e) {
            ctx.addLog("Warning: failed to remove image " + imageName + ": " + e.getMessage());
        }
    }

    private void cleanupDatabase(DeploymentContext ctx) {
        String slug = ctx.getProjectSlug();
        String dbName = NamingUtils.dbName(slug);

        ctx.addLog("Dropping database...");
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/postgres", externalDbHost, externalDbPort);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, pricesUser, pricesPassword)) {
            // Terminate existing connections
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
            String dropDbSql = String.format("DROP DATABASE IF EXISTS \"%s\"", dbName);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(dropDbSql);
            }
            ctx.addLog(String.format("Dropped database: %s", dbName));

        } catch (SQLException e) {
            ctx.addLog(String.format("Warning: database cleanup failed: %s", e.getMessage()));
            log.error("Failed to cleanup database for project: {}", ctx.getProjectSlug(), e);
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
