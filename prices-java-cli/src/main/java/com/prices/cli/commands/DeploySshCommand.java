package com.prices.cli.commands;

import com.prices.cli.util.ZipUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "deploy-ssh", description = "Deploy a project via SSH tunnel (direct to server)")
public class DeploySshCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project directory", defaultValue = ".")
    private File path;

    // SSH Connection
    @Option(names = {"--host"}, description = "SSH host (default: localhost for tunnel)")
    private String host;

    @Option(names = {"--port"}, description = "SSH port (default: 9999 for tunnel)")
    private Integer port;

    @Option(names = {"--username", "-u"}, description = "SSH username (default: root)")
    private String username;

    @Option(names = {"--private-key", "-i"}, description = "Path to SSH private key")
    private String privateKey;

    // Deployment args (passed to fullstack-deploy.sh)
    @Option(names = {"--slug", "-s"}, description = "Project slug (unique identifier)", required = true)
    private String slug;

    @Option(names = {"--frontend-url"}, description = "Frontend URL")
    private String frontendUrl;

    @Option(names = {"--backend-url"}, description = "Backend URL")
    private String backendUrl;

    @Option(names = {"--custom-frontend-url"}, description = "Custom frontend URL")
    private String customFrontendUrl;

    @Option(names = {"--custom-backend-url"}, description = "Custom backend URL")
    private String customBackendUrl;

    @Option(names = {"--monitoring-url"}, description = "Monitoring URL")
    private String monitoringUrl;

    @Option(names = {"--custom-monitoring-url"}, description = "Custom monitoring URL")
    private String customMonitoringUrl;

    @Option(names = {"--expose-monitoring"}, description = "Expose monitoring endpoint")
    private boolean exposeMonitoring;

    @Option(names = {"--env-file"}, description = "Path to local .env file to copy")
    private File envFile;

    @Option(names = {"-e", "--env"}, description = "Environment variables (KEY=VALUE)")
    private Map<String, String> envVars;

    @Option(names = {"--redeploy"}, description = "Preserve volumes on redeploy")
    private boolean redeploy;

    @Option(names = {"--dry-run"}, description = "Show what would be done without executing")
    private boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompts")
    private boolean skipConfirm;

    private static final String REMOTE_DEPLOY_SCRIPT = "/var/prices/deployment-scripts/fullstack-deploy.sh";
    private static final String REMOTE_TMP_DIR = "/tmp";

    @Override
    public Integer call() throws Exception {
        // 1. Validate path
        Path absPath = path.toPath().toAbsolutePath();
        if (!Files.exists(absPath)) {
            System.err.println("Path does not exist: " + absPath);
            return 1;
        }

        validateProject(absPath);

        // 2. Prompt for required args if not provided
        if (!promptRequiredArgs()) {
            return 1;
        }

        // 3. Show summary
        System.out.println("\n=== SSH Deployment ===");
        System.out.printf("  Project: %s%n", absPath);
        System.out.printf("  Slug: %s%n", slug);
        System.out.printf("  SSH: %s@%s:%d%n", username, host, port);
        System.out.printf("  Private Key: %s%n", privateKey);
        if (dryRun) {
            System.out.println("  Mode: DRY RUN");
        }
        System.out.println();

        if (!skipConfirm && !dryRun) {
            System.out.print("Continue? (Y/n): ");
            String confirm = System.console().readLine().trim().toLowerCase();
            if (confirm.equals("n") || confirm.equals("no")) {
                System.out.println("Aborted.");
                return 0;
            }
        }

        // 4. Create archive
        System.out.println("Preparing files...");
        Path archivePath = ZipUtil.createProjectArchive(absPath);
        long fileSizeBytes = Files.size(archivePath);
        String fileSize = formatFileSize(fileSizeBytes);
        System.out.printf("Archive created: %s (%s)%n", archivePath.getFileName(), fileSize);

        String remoteArtifact = REMOTE_TMP_DIR + "/artifact-" + System.currentTimeMillis() + ".zip";

        try {
            // 5. SCP artifact to server
            System.out.println("\nUploading artifact to server...");
            if (!dryRun) {
                int scpResult = runScp(archivePath.toString(), remoteArtifact);
                if (scpResult != 0) {
                    System.err.println("Failed to upload artifact");
                    return 1;
                }
                System.out.println("✓ Artifact uploaded");
            } else {
                System.out.println("[DRY RUN] Would upload: " + archivePath + " -> " + remoteArtifact);
            }

            // 6. Copy env file if provided
            String remoteEnvFile = null;
            if (envFile != null && envFile.exists()) {
                remoteEnvFile = REMOTE_TMP_DIR + "/env-" + System.currentTimeMillis() + ".env";
                System.out.println("Uploading env file...");
                if (!dryRun) {
                    int scpEnv = runScp(envFile.getAbsolutePath(), remoteEnvFile);
                    if (scpEnv != 0) {
                        System.err.println("Warning: Failed to upload env file");
                        remoteEnvFile = null;
                    } else {
                        System.out.println("✓ Env file uploaded");
                    }
                } else {
                    System.out.println("[DRY RUN] Would upload: " + envFile + " -> " + remoteEnvFile);
                }
            }

            // 7. Build remote command
            String remoteCommand = buildRemoteCommand(remoteArtifact, remoteEnvFile);
            System.out.println("\nExecuting deployment...");
            if (parent != null && parent.isVerbose()) {
                System.out.println("Command: " + remoteCommand);
            }

            // 8. SSH exec with live output
            if (!dryRun) {
                int sshResult = runSshWithLiveOutput(remoteCommand);
                
                // 9. Cleanup remote files
                System.out.println("\nCleaning up...");
                runSsh("rm -f " + remoteArtifact + (remoteEnvFile != null ? " " + remoteEnvFile : ""));
                
                if (sshResult == 0) {
                    System.out.println("\n✓ Deployment completed successfully!");
                    return 0;
                } else {
                    System.err.println("\n✗ Deployment failed with exit code: " + sshResult);
                    return sshResult;
                }
            } else {
                System.out.println("[DRY RUN] Would execute: " + remoteCommand);
                return 0;
            }

        } finally {
            Files.deleteIfExists(archivePath);
        }
    }

    private boolean promptRequiredArgs() {
        java.io.Console console = System.console();
        if (console == null) {
            System.err.println("No console available for interactive input");
            return privateKey != null;
        }

        // Host (default: localhost)
        if (host == null || host.isEmpty()) {
            System.out.print("SSH Host [localhost]: ");
            String input = console.readLine().trim();
            host = input.isEmpty() ? "localhost" : input;
        }

        // Port (default: 9999)
        if (port == null) {
            System.out.print("SSH Port [9999]: ");
            String input = console.readLine().trim();
            port = input.isEmpty() ? 9999 : Integer.parseInt(input);
        }

        // Username (default: root)
        if (username == null || username.isEmpty()) {
            System.out.print("SSH Username [root]: ");
            String input = console.readLine().trim();
            username = input.isEmpty() ? "root" : input;
        }

        // Private key (required)
        if (privateKey == null || privateKey.isEmpty()) {
            System.out.print("SSH Private Key path: ");
            privateKey = console.readLine().trim();
            if (privateKey.isEmpty()) {
                System.err.println("Private key is required");
                return false;
            }
        }

        // Validate private key exists
        if (!new File(privateKey).exists()) {
            System.err.println("Private key not found: " + privateKey);
            return false;
        }

        return true;
    }

    private void validateProject(Path path) throws IOException {
        Path frontend = path.resolve("frontend");
        if (!Files.exists(frontend) || !Files.isDirectory(frontend)) {
            throw new IOException("frontend/ folder not found in " + path);
        }
        Path backend = path.resolve("backend");
        if (!Files.exists(backend) || !Files.isDirectory(backend)) {
            throw new IOException("backend/ folder not found in " + path);
        }
    }

    private String buildRemoteCommand(String remoteArtifact, String remoteEnvFile) {
        StringBuilder cmd = new StringBuilder();
        cmd.append(REMOTE_DEPLOY_SCRIPT);
        cmd.append(" --artifact ").append(remoteArtifact);
        cmd.append(" --slug ").append(slug);

        if (frontendUrl != null) cmd.append(" --frontend-url ").append(frontendUrl);
        if (backendUrl != null) cmd.append(" --backend-url ").append(backendUrl);
        if (customFrontendUrl != null) cmd.append(" --custom-frontend-url ").append(customFrontendUrl);
        if (customBackendUrl != null) cmd.append(" --custom-backend-url ").append(customBackendUrl);
        if (monitoringUrl != null) cmd.append(" --monitoring-url ").append(monitoringUrl);
        if (customMonitoringUrl != null) cmd.append(" --custom-monitoring-url ").append(customMonitoringUrl);
        if (exposeMonitoring) cmd.append(" --expose-monitoring");
        if (remoteEnvFile != null) cmd.append(" --env-file ").append(remoteEnvFile);
        if (redeploy) cmd.append(" --redeploy");
        if (dryRun) cmd.append(" --dry-run");

        // Add individual env vars
        if (envVars != null) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                cmd.append(" --env ").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        return cmd.toString();
    }

    private int runScp(String localPath, String remotePath) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("UserKnownHostsFile=/dev/null");
        cmd.add("-i");
        cmd.add(privateKey);
        cmd.add("-P");
        cmd.add(String.valueOf(port));
        cmd.add(localPath);
        cmd.add(username + "@" + host + ":" + remotePath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        return p.waitFor();
    }

    private int runSsh(String remoteCommand) throws Exception {
        List<String> cmd = buildSshCommand(remoteCommand);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        return p.waitFor();
    }

    private int runSshWithLiveOutput(String remoteCommand) throws Exception {
        List<String> cmd = buildSshCommand(remoteCommand);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        // Stream output live
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        return p.waitFor();
    }

    private List<String> buildSshCommand(String remoteCommand) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("UserKnownHostsFile=/dev/null");
        cmd.add("-i");
        cmd.add(privateKey);
        cmd.add("-p");
        cmd.add(String.valueOf(port));
        cmd.add(username + "@" + host);
        cmd.add(remoteCommand);
        return cmd;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), unit);
    }
}
