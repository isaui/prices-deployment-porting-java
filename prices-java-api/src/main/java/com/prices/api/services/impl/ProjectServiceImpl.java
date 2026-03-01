package com.prices.api.services.impl;

import com.prices.api.config.DatabaseConfig;
import com.prices.api.constants.Constants;
import com.prices.api.dto.requests.CreateProjectRequest;
import com.prices.api.dto.requests.UpdateProjectRequest;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.Project;
import com.prices.api.repositories.DeploymentHistoryRepository;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.services.ProjectService;
import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.stages.CleanupStage;
import com.prices.api.utils.EnvUtils;
import com.prices.api.utils.NamingUtils;
import com.prices.api.utils.SlugUtils;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepo;
    private final DeploymentHistoryRepository deploymentRepo;
    private final DatabaseConfig databaseConfig;

    @Override
    @Transactional
    public Project create(Long userId, CreateProjectRequest req) {
        String slug = SlugUtils.generateSlug(req.getName());
        String parentDomain = EnvUtils.getParentDomain();

        String customFrontendURL = req.getCustomFrontendUrl() != null && !req.getCustomFrontendUrl().isEmpty()
                ? req.getCustomFrontendUrl()
                : null;
        String customBackendURL = req.getCustomBackendUrl() != null && !req.getCustomBackendUrl().isEmpty()
                ? req.getCustomBackendUrl()
                : null;
        String customMonitoringURL = req.getCustomMonitoringUrl() != null && !req.getCustomMonitoringUrl().isEmpty()
                ? req.getCustomMonitoringUrl()
                : null;

        Project project = new Project();
        project.setName(req.getName());
        project.setSlug(slug);
        project.setDescription(req.getDescription());
        project.setStatus("pending");
        project.setUserId(userId);

        // Frontend URLs
        project.setDefaultFrontendUrl("frontend-" + slug + "." + parentDomain);
        project.setCustomFrontendUrl(customFrontendURL);
        project.setDefaultFrontendActive(true);
        project.setCustomFrontendActive(customFrontendURL != null);

        // Backend URLs
        project.setDefaultBackendUrl("backend-" + slug + "." + parentDomain);
        project.setCustomBackendUrl(customBackendURL);
        project.setDefaultBackendActive(true);
        project.setCustomBackendActive(customBackendURL != null);

        // Monitoring URLs
        project.setDefaultMonitoringUrl("monitoring-" + slug + "." + parentDomain);
        project.setCustomMonitoringUrl(customMonitoringURL);
        project.setDefaultMonitoringActive(req.isNeedMonitoringExposed());
        project.setCustomMonitoringActive(req.isNeedMonitoringExposed() && customMonitoringURL != null);
        project.setNeedMonitoringExposed(req.isNeedMonitoringExposed());

        // Listening ports
        if (req.getFrontendListeningPort() != null) {
            project.setFrontendListeningPort(req.getFrontendListeningPort());
        }
        if (req.getBackendListeningPort() != null) {
            project.setBackendListeningPort(req.getBackendListeningPort());
        }

        return projectRepo.save(project);
    }

    @Override
    public Project getById(Long id) {
        return projectRepo.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
    }

    @Override
    public Project getBySlug(String slug) {
        return projectRepo.findBySlug(slug).orElseThrow(() -> new RuntimeException("Project not found"));
    }

    @Override
    public List<Project> getByUserId(Long userId) {
        return projectRepo.findByUserId(userId);
    }

    @Override
    public List<Project> getAll() {
        return projectRepo.findAll();
    }

    @Override
    @Transactional
    public Project update(Long id, UpdateProjectRequest req) {
        Project project = getById(id);

        if (req.getName() != null && !req.getName().isEmpty()) {
            project.setName(req.getName());
        }
        if (req.getDescription() != null) {
            project.setDescription(req.getDescription());
        }

        // Frontend custom URL
        if (req.getCustomFrontendUrl() != null && !req.getCustomFrontendUrl().isEmpty()) {
            project.setCustomFrontendUrl(req.getCustomFrontendUrl());
            if (req.getIsCustomFrontendActive() == null) {
                project.setCustomFrontendActive(true);
            }
        }
        if (req.getIsDefaultFrontendActive() != null) {
            project.setDefaultFrontendActive(req.getIsDefaultFrontendActive());
        }
        if (req.getIsCustomFrontendActive() != null) {
            project.setCustomFrontendActive(req.getIsCustomFrontendActive());
        }

        // Backend custom URL
        if (req.getCustomBackendUrl() != null && !req.getCustomBackendUrl().isEmpty()) {
            project.setCustomBackendUrl(req.getCustomBackendUrl());
            if (req.getIsCustomBackendActive() == null) {
                project.setCustomBackendActive(true);
            }
        }
        if (req.getIsDefaultBackendActive() != null) {
            project.setDefaultBackendActive(req.getIsDefaultBackendActive());
        }
        if (req.getIsCustomBackendActive() != null) {
            project.setCustomBackendActive(req.getIsCustomBackendActive());
        }

        // Monitoring custom URL
        if (req.getCustomMonitoringUrl() != null && !req.getCustomMonitoringUrl().isEmpty()) {
            project.setCustomMonitoringUrl(req.getCustomMonitoringUrl());
            if (req.getIsCustomMonitoringActive() == null) {
                project.setCustomMonitoringActive(true);
            }
        }
        if (req.getIsDefaultMonitoringActive() != null) {
            project.setDefaultMonitoringActive(req.getIsDefaultMonitoringActive());
        }
        if (req.getIsCustomMonitoringActive() != null) {
            project.setCustomMonitoringActive(req.getIsCustomMonitoringActive());
        }
        if (req.getNeedMonitoringExposed() != null) {
            project.setNeedMonitoringExposed(req.getNeedMonitoringExposed());
        }

        // Listening ports
        if (req.getFrontendListeningPort() != null) {
            project.setFrontendListeningPort(req.getFrontendListeningPort());
        }
        if (req.getBackendListeningPort() != null) {
            project.setBackendListeningPort(req.getBackendListeningPort());
        }

        return projectRepo.update(project);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 1. Get project info for cleanup
        Project project = getById(id);

        // 2. Run cleanup pipeline to remove containers, nginx, files
        try {
            Path deployDir = Paths.get(Constants.DEPLOYMENTS_BASE_DIR, project.getSlug());
            DeploymentContext ctx = new DeploymentContext();
            ctx.setProjectSlug(project.getSlug());
            ctx.setExtractedPath(deployDir);
            ctx.setComposePath(deployDir.resolve("docker-compose.yml"));

            // Re-use logic from CleanupStage
            new CleanupStage(
                    detectDockerComposeCmd(),
                    databaseConfig.getHost(),
                    databaseConfig.getPort(),
                    databaseConfig.getPricesUser(),
                    databaseConfig.getPricesPassword()
            ).execute(ctx);

        } catch (Exception e) {
            log.warn("Cleanup pipeline failed for project: {}", project.getSlug(), e);
            // Continue with database deletion
        }

        // 3. Delete deployment histories first (FK constraint)
        deploymentRepo.deleteByProjectId(id);
        
        // 4. Delete project from database
        projectRepo.deleteById(id);
    }

    @Override
    public List<DeploymentHistory> getDeploymentHistory(Long projectId) {
        return deploymentRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Override
    public String getLogs(Long projectId, int lines) {
        Project project = getById(projectId);

        if (lines < 1)
            lines = 50;
        if (lines > 1000)
            lines = 1000;

        Path composePath = Paths.get(Constants.DEPLOYMENTS_BASE_DIR, project.getSlug(), "docker-compose.yml");
        String projectName = NamingUtils.projectName(project.getSlug());

        // Detect docker compose command
        String[] dockerComposeCmd = detectDockerComposeCmd();

        // Build command
        // docker-compose -f <path> -p <name> logs --tail <lines>
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(dockerComposeCmd[0]);
        if (dockerComposeCmd.length > 1)
            pb.command().add(dockerComposeCmd[1]);

        pb.command().add("-f");
        pb.command().add(composePath.toString());
        pb.command().add("-p");
        pb.command().add(projectName);
        pb.command().add("logs");
        pb.command().add("--tail");
        pb.command().add(String.valueOf(lines));

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            // Also capture stderr
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            return output.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get logs", e);
        }
    }

    @Override
    public Flux<String> streamLogs(Long projectId) {
        return Flux.create(emitter -> {
            Project project;
            try {
                project = getById(projectId);
            } catch (Exception e) {
                emitter.error(e);
                return;
            }

            Path composePath = Paths.get(Constants.DEPLOYMENTS_BASE_DIR, project.getSlug(), "docker-compose.yml");
            String projectName = NamingUtils.projectName(project.getSlug());

            String[] dockerComposeCmd = detectDockerComposeCmd();

            // docker-compose -f <path> -p <name> logs -f --tail=100
            ProcessBuilder pb = new ProcessBuilder();
            pb.command().add(dockerComposeCmd[0]);
            if (dockerComposeCmd.length > 1)
                pb.command().add(dockerComposeCmd[1]);

            pb.command().add("-f");
            pb.command().add(composePath.toString());
            pb.command().add("-p");
            pb.command().add(projectName);
            pb.command().add("logs");
            pb.command().add("-f");
            pb.command().add("--tail=100");

            try {
                Process process = pb.start();

                // Handle process destroy when subscriber cancels
                emitter.onDispose(process::destroy);

                // Read stdout
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !emitter.isCancelled()) {
                            emitter.next(line);
                        }
                    } catch (IOException e) {
                        if (!emitter.isCancelled()) {
                            emitter.error(e);
                        }
                    }
                }).start();

                // Read stderr
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !emitter.isCancelled()) {
                            emitter.next(line);
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }).start();

                // Wait for process exit in a separate thread to complete the emitter
                new Thread(() -> {
                    try {
                        process.waitFor();
                        emitter.complete();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }).start();

            } catch (IOException e) {
                emitter.error(e);
            }
        });
    }

    @Override
    public Map<String, String> getEnvVars(Long projectId) {
        Project project = getById(projectId);
        if (project.getEnvVars() == null) {
            return new HashMap<>();
        }
        return project.getEnvVars();
    }

    @Override
    @Transactional
    public void updateEnvVars(Long projectId, Map<String, String> envVars) {
        Project project = getById(projectId);
        project.setEnvVars(envVars);
        projectRepo.update(project);
    }

    @Override
    @Transactional
    public void upsertEnvVars(Long projectId, Map<String, String> envVars) {
        Project project = getById(projectId);
        Map<String, String> current = project.getEnvVars();
        if (current == null) {
            current = new HashMap<>();
        }
        current.putAll(envVars);
        project.setEnvVars(current);
        projectRepo.update(project);
    }

    private String[] detectDockerComposeCmd() {
        // Simple check, real implementation might need to execute command to check
        // version
        try {
            Process p = new ProcessBuilder("docker", "compose", "version").start();
            if (p.waitFor() == 0) {
                return new String[] { "docker", "compose" };
            }
        } catch (Exception e) {
            // ignore
        }
        return new String[] { "docker-compose" };
    }

    @Override
    @Transactional
    public Project createInternal(Project project) {
        String parentDomain = EnvUtils.getParentDomain();
        
        // Set default URLs if not provided
        if (project.getDefaultFrontendUrl() == null) {
            project.setDefaultFrontendUrl("frontend-" + project.getSlug() + "." + parentDomain);
        }
        if (project.getDefaultBackendUrl() == null) {
            project.setDefaultBackendUrl("backend-" + project.getSlug() + "." + parentDomain);
        }
        if (project.getDefaultMonitoringUrl() == null) {
            project.setDefaultMonitoringUrl("monitoring-" + project.getSlug() + "." + parentDomain);
        }
        
        // Ensure status is set
        if (project.getStatus() == null) {
            project.setStatus("pending");
        }
        
        return projectRepo.save(project);
    }
}
