package com.prices.cli.commands;

import com.prices.cli.util.ZipUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import com.prices.cli.config.ConfigManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Deploy via SSH - SCP artifact to server, then SSH exec deploy.sh.
 * Requires SSH access configured in ~/.ssh/config.
 */
@Command(name = "deploy-ssh", description = "Deploy via SSH (requires ~/.ssh/config)")
public class DeploySshCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project directory", defaultValue = ".")
    private File path;

    @Option(names = {"--ssh-host", "-H"}, description = "SSH host (from ~/.ssh/config)", required = true)
    private String sshHost;

    @Option(names = {"--project-name", "-n"}, description = "Project name", required = true)
    private String projectName;

    @Option(names = {"--frontend-url"}, description = "Custom frontend URL")
    private String frontendUrl;

    @Option(names = {"--backend-url"}, description = "Custom backend URL")
    private String backendUrl;

    @Option(names = {"--frontend-port"}, description = "Frontend port (default: 80)")
    private Integer frontendPort = 80;

    @Option(names = {"--backend-port"}, description = "Backend port (default: 7776)")
    private Integer backendPort = 7776;

    @Option(names = {"-e", "--env"}, description = "Environment variables (KEY=VALUE)")
    private Map<String, String> envVars;

    @Option(names = {"--deploy-script"}, description = "Remote deploy script path (default: from config)")
    private String deployScript;

    @Option(names = {"--remote-tmp"}, description = "Remote tmp directory (default: from config)")
    private String remoteTmp;

    @Option(names = {"--dry-run"}, description = "Show what would be done")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absPath = path.toPath().toAbsolutePath();
        if (!Files.exists(absPath)) {
            System.err.println("Path does not exist: " + absPath);
            return 1;
        }

        ConfigManager configManager = new ConfigManager();
        String remoteDeployScript = deployScript != null ? deployScript : configManager.getSshDeployScript();
        String remoteTmpDir = remoteTmp != null ? remoteTmp : configManager.getSshRemoteTmp();

        validateProject(absPath);

        // Show summary
        System.out.println("\n=== SSH Deployment ===");
        System.out.println("  SSH Host: " + sshHost);
        System.out.println("  Project: " + projectName);
        System.out.println("  Path: " + absPath);
        if (frontendUrl != null) System.out.println("  Frontend URL: " + frontendUrl);
        if (backendUrl != null) System.out.println("  Backend URL: " + backendUrl);
        System.out.println("  Frontend Port: " + frontendPort);
        System.out.println("  Backend Port: " + backendPort);
        System.out.println("  Deploy Script: " + remoteDeployScript);
        System.out.println("  Remote Tmp: " + remoteTmpDir);
        if (dryRun) System.out.println("  Mode: DRY RUN");
        System.out.println();

        // 1. Create archive
        System.out.println("Creating archive...");
        Path archivePath = ZipUtil.createProjectArchive(absPath);
        System.out.println("Archive: " + formatFileSize(Files.size(archivePath)));

        String remoteArtifact = remoteTmpDir + "/artifact-" + System.currentTimeMillis() + ".zip";

        try {
            // 2. SCP artifact to server
            System.out.println("\nUploading artifact...");
            if (!dryRun) {
                int scpResult = runScp(archivePath.toString(), remoteArtifact);
                if (scpResult != 0) {
                    System.err.println("SCP failed. Check SSH config: ~/.ssh/config");
                    return 1;
                }
                System.out.println("✓ Artifact uploaded");
            } else {
                System.out.println("[DRY RUN] scp " + archivePath + " " + sshHost + ":" + remoteArtifact);
            }

            // 3. Execute deploy.sh
            String deployCmd = buildDeployCommand(remoteArtifact, remoteDeployScript);
            System.out.println("\nDeploying...");
            if (parent != null && parent.isVerbose()) {
                System.out.println("$ ssh " + sshHost + " " + deployCmd);
            }

            if (!dryRun) {
                int result = runSsh(deployCmd);
                runSsh("rm -f " + remoteArtifact);

                if (result == 0) {
                    System.out.println("\n✓ Deployment complete!");
                } else {
                    System.err.println("\n✗ Deployment failed (exit " + result + ")");
                }
                return result;
            } else {
                System.out.println("[DRY RUN] ssh " + sshHost + " " + deployCmd);
                return 0;
            }

        } finally {
            Files.deleteIfExists(archivePath);
        }
    }

    private void validateProject(Path projectPath) throws IOException {
        if (!Files.exists(projectPath.resolve("frontend")) && !Files.exists(projectPath.resolve("backend"))) {
            throw new IOException("No frontend/ or backend/ folder found");
        }
    }

    private String buildDeployCommand(String remoteArtifact, String remoteDeployScript) {
        // Run deploy script directly - script uses echo for immediate output
        StringBuilder cmd = new StringBuilder(remoteDeployScript);
        cmd.append(" --project-name ").append(quote(projectName));
        cmd.append(" --artifact ").append(remoteArtifact);
        if (frontendUrl != null) cmd.append(" --frontend-url ").append(quote(frontendUrl));
        if (backendUrl != null) cmd.append(" --backend-url ").append(quote(backendUrl));
        cmd.append(" --frontend-port ").append(frontendPort);
        cmd.append(" --backend-port ").append(backendPort);
        if (envVars != null) {
            for (Map.Entry<String, String> e : envVars.entrySet()) {
                cmd.append(" --env ").append(quote(e.getKey() + "=" + e.getValue()));
            }
        }
        return cmd.toString();
    }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private int runScp(String localPath, String remotePath) throws Exception {
        String[] cmd = {"scp", localPath, sshHost + ":" + remotePath};
        return runCommand(cmd);
    }

    private int runSsh(String remoteCmd) throws Exception {
        // Use -t for TTY allocation
        String[] cmd = {"ssh", "-t", sshHost, remoteCmd};
        return runCommand(cmd);
    }

    /**
     * Run command with inheritIO - uses the real terminal for input/output.
     * This allows SSH to properly handle passphrase prompts.
     */
    private int runCommand(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();  // Use parent process's stdin/stdout/stderr
        Process process = pb.start();
        return process.waitFor();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }
}
