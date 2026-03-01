package com.prices.cli.commands;

import com.prices.cli.util.ZipUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

    @Option(names = {"--dry-run"}, description = "Show what would be done")
    private boolean dryRun;

    private static final String REMOTE_DEPLOY_SCRIPT = "/home/admin/deployment/agent/deployment-scripts/deploy.sh";
    private static final String REMOTE_TMP = "/tmp";

    @Override
    public Integer call() throws Exception {
        Path absPath = path.toPath().toAbsolutePath();
        if (!Files.exists(absPath)) {
            System.err.println("Path does not exist: " + absPath);
            return 1;
        }

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
        if (dryRun) System.out.println("  Mode: DRY RUN");
        System.out.println();

        // 1. Create archive
        System.out.println("Creating archive...");
        Path archivePath = ZipUtil.createProjectArchive(absPath);
        System.out.println("Archive: " + formatFileSize(Files.size(archivePath)));

        String remoteArtifact = REMOTE_TMP + "/artifact-" + System.currentTimeMillis() + ".zip";

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
            String deployCmd = buildDeployCommand(remoteArtifact);
            System.out.println("\nDeploying...");
            if (parent != null && parent.isVerbose()) {
                System.out.println("$ ssh " + sshHost + " " + deployCmd);
            }

            if (!dryRun) {
                int result = runSsh(deployCmd, true);
                runSsh("rm -f " + remoteArtifact, false);

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

    private String buildDeployCommand(String remoteArtifact) {
        // Use stdbuf to disable output buffering for real-time streaming
        StringBuilder cmd = new StringBuilder("stdbuf -oL -eL " + REMOTE_DEPLOY_SCRIPT);
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
        String[] cmd = {"scp", "-o", "BatchMode=no", localPath, sshHost + ":" + remotePath};
        return runInteractiveCommand(cmd);
    }

    private int runSsh(String remoteCmd, boolean showOutput) throws Exception {
        String[] cmd = {"ssh", "-o", "BatchMode=no", "-t", sshHost, remoteCmd};
        return runInteractiveCommand(cmd);
    }

    /**
     * Run command in a pseudo-terminal (PTY) for interactive prompts.
     * SSH reads passphrase from /dev/tty, so we need a real PTY.
     */
    private int runInteractiveCommand(String[] cmd) throws Exception {
        // Setup PTY environment
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm");
        
        // Create PTY process with console mode for Windows
        PtyProcess pty = new PtyProcessBuilder(cmd)
            .setEnvironment(env)
            .setRedirectErrorStream(true)
            .setConsole(true)  // Enable console mode
            .setInitialColumns(120)
            .setInitialRows(40)
            .start();
        
        InputStream ptyOut = pty.getInputStream();
        OutputStream ptyIn = pty.getOutputStream();
        
        // Forward PTY output to CLI stdout - read byte by byte for immediate output
        Thread outputThread = new Thread(() -> {
            try {
                int b;
                while ((b = ptyOut.read()) != -1) {
                    System.out.write(b);
                    System.out.flush();
                }
            } catch (IOException e) {
                // PTY closed
            }
        });
        outputThread.setDaemon(true);
        outputThread.start();
        
        // Forward CLI stdin to PTY input - read byte by byte for immediate response
        Thread inputThread = new Thread(() -> {
            try {
                int b;
                while (pty.isAlive()) {
                    if (System.in.available() > 0) {
                        b = System.in.read();
                        if (b == -1) break;
                        ptyIn.write(b);
                        ptyIn.flush();
                    } else {
                        Thread.sleep(10); // Small delay to avoid busy-waiting
                    }
                }
            } catch (IOException | InterruptedException e) {
                // PTY closed or stdin closed
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
        
        int exitCode = pty.waitFor();
        outputThread.join(1000);
        return exitCode;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }
}
