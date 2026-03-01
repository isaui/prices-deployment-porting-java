package com.prices.cli.commands;

import com.prices.cli.api.Client;
import com.prices.cli.api.models.CreateProjectRequest;
import com.prices.cli.api.models.Deployment;
import com.prices.cli.api.models.Project;
import com.prices.cli.config.ConfigManager;
import com.prices.cli.util.UrlUtil;
import com.prices.cli.util.ZipUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "deploy", description = "Deploy a project to the Prices platform")
public class DeployCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project path", defaultValue = ".")
    private File path;

    @Option(names = {"-p", "--project"}, description = "Target project slug")
    private String projectSlug;

    @Option(names = {"-n", "--new"}, description = "Force create new project")
    private boolean forceNew;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompts")
    private boolean skipConfirm;

    @Option(names = {"--version"}, description = "Deployment version", defaultValue = "1.0.0")
    private String version;

    private Client client;

    @Override
    public Integer call() throws Exception {
        // 1. Validate path
        Path absPath = path.toPath().toAbsolutePath();
        if (!Files.exists(absPath)) {
            System.err.println("Path does not exist: " + absPath);
            return 1;
        }
        
        validateProject(absPath);

        // 2. Auth
        ConfigManager configManager = new ConfigManager();
        String token = configManager.getToken();
        if (token == null || token.isEmpty()) {
            System.err.println("Not logged in. Run 'prices login' first.");
            return 1;
        }

        client = new Client(configManager.getApiUrl());
        client.setToken(token);

        // 3. Determine Project
        String targetSlug = "";
        
        if (projectSlug != null && !projectSlug.isEmpty() && !forceNew) {
            Project p = client.getProject(projectSlug);
            targetSlug = p.getSlug();
            System.out.printf("✓ Deploying to existing project: %s (ID: %d)%n", targetSlug, p.getId());
        } else if (forceNew) {
            targetSlug = createNewProject(absPath);
        } else {
            List<Project> projects = client.listProjects();
            if (projects.isEmpty()) {
                targetSlug = createNewProject(absPath);
            } else {
                targetSlug = promptExistingOrNew(projects, absPath);
            }
        }
        
        if (targetSlug == null) {
            return 1; // Aborted
        }

        // 4. Create Archive
        System.out.println("Preparing files...");
        Path archivePath = ZipUtil.createProjectArchive(absPath);
        long fileSizeBytes = Files.size(archivePath);
        String fileSize = formatFileSize(fileSizeBytes);
        System.out.printf("Archive created: %s%n", fileSize);
        
        try {
            // Deploy with progress
            System.out.print("Uploading: 0.00 MB / " + fileSize + " (0%)");
            String deploymentId = client.deploy(targetSlug, archivePath, version, (uploaded, total) -> {
                double uploadedMB = uploaded / (1024.0 * 1024.0);
                double totalMB = total / (1024.0 * 1024.0);
                int percent = (int) ((uploaded * 100) / total);
                System.out.printf("\rUploading: %.2f MB / %.2f MB (%d%%)", uploadedMB, totalMB, percent);
            });
            System.out.println(); // New line after progress
            
            // Stream deployment logs (blocks until deployment finishes)
            System.out.println("Deploying...");
            try {
                client.streamDeploymentLogs(deploymentId, System.out::println);
            } catch (Exception e) {
                if (parent.isVerbose()) {
                    System.out.println("Warning: could not stream logs: " + e.getMessage());
                }
            }
            
            // Get final status (stream already waited for completion)
            Deployment dep = client.getDeploymentStatus(deploymentId);
            
            if ("success".equalsIgnoreCase(dep.getStatus())) {
                System.out.println("\n✓ Deployed successfully!");
                Project p = dep.getProject();
                if (p != null) {
                    if (p.isDefaultFrontendActive() && p.getDefaultFrontendURL() != null) {
                        System.out.println("  Frontend: " + UrlUtil.fullUrl(p.getDefaultFrontendURL()));
                    }
                    if (p.isDefaultBackendActive() && p.getDefaultBackendURL() != null) {
                        System.out.println("  Backend:  " + UrlUtil.fullUrl(p.getDefaultBackendURL()));
                    }
                    if (p.isNeedMonitoringExposed() && p.getDefaultMonitoringURL() != null) {
                        System.out.println("  Monitoring: " + UrlUtil.fullUrl(p.getDefaultMonitoringURL()));
                    }
                }
                System.out.println("  Status: prices status " + targetSlug);
                System.out.println("  Logs: prices logs " + targetSlug);
                return 0;
            } else {
                System.out.println("\n✗ Deployment failed: " + (dep.getError() != null ? dep.getError() : "Unknown error"));
                return 1;
            }
            
        } finally {
            Files.deleteIfExists(archivePath);
        }
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

    private String createNewProject(Path path) throws Exception {
        // Normalize path to get actual directory name (not "." if user passed ".")
        String defaultName = path.normalize().toAbsolutePath().getFileName().toString();
        
        System.out.printf("Project name [%s]: ", defaultName);
        String name = System.console().readLine();
        if (name == null || name.trim().isEmpty()) {
            name = defaultName;
        } else {
            name = name.trim();
        }

        System.out.print("Custom frontend domain (optional, press Enter to skip): ");
        String cf = System.console().readLine().trim();
        
        System.out.print("Custom backend domain (optional, press Enter to skip): ");
        String cb = System.console().readLine().trim();
        
        System.out.print("Expose monitoring endpoint? (y/N): ");
        String mon = System.console().readLine().trim().toLowerCase();
        boolean exposeMon = mon.equals("y") || mon.equals("yes");
        
        String cm = "";
        if (exposeMon) {
            System.out.print("Custom monitoring domain (optional, press Enter to skip): ");
            cm = System.console().readLine().trim();
        }

        System.out.println("Creating project: " + name);
        CreateProjectRequest req = new CreateProjectRequest(name, "", cf, cb, cm, exposeMon, null, null);
        Project p = client.createProject(req);
        
        System.out.printf("✓ Project created: %s (ID: %d)%n", p.getSlug(), p.getId());
        return p.getSlug();
    }

    private String promptExistingOrNew(List<Project> projects, Path path) throws Exception {
        System.out.println("\nExisting projects:");
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            System.out.printf("  [%d] %s (%s)%n", i + 1, p.getSlug(), p.getStatus());
        }
        System.out.println("  [0] Create new project\n");
        
        System.out.print("Select project [0]: ");
        String input = System.console().readLine().trim();
        
        if (input.isEmpty() || input.equals("0")) {
            return createNewProject(path);
        }
        
        try {
            int idx = Integer.parseInt(input);
            if (idx < 1 || idx > projects.size()) {
                throw new NumberFormatException();
            }
            Project selected = projects.get(idx - 1);
            System.out.printf("✓ Using existing project: %s (ID: %d)%n", selected.getSlug(), selected.getId());
            return selected.getSlug();
        } catch (NumberFormatException e) {
            System.err.println("Invalid selection");
            return null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), unit);
    }
}
