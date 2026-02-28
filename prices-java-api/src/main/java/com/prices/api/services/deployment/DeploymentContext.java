package com.prices.api.services.deployment;

import com.prices.api.models.Project;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Data
@Slf4j
public class DeploymentContext {
    // Input
    private String projectSlug;
    private byte[] artifactData;

    // Frontend URLs
    private String defaultFrontendURL;
    private String customFrontendURL;
    private boolean isDefaultFrontendActive;
    private boolean isCustomFrontendActive;

    // Backend URLs
    private String defaultBackendURL;
    private String customBackendURL;
    private boolean isDefaultBackendActive;
    private boolean isCustomBackendActive;

    // Monitoring URLs
    private String defaultMonitoringURL;
    private String customMonitoringURL;
    private boolean isDefaultMonitoringActive;
    private boolean isCustomMonitoringActive;
    private boolean needMonitoringExposed;

    // Listening ports (for Traefik routing)
    private int frontendListeningPort = 3000;
    private int backendListeningPort = 7776;

    // Env vars
    private Map<String, String> existingEnvVars = new HashMap<>();
    private Map<String, String> inputEnvVars = new HashMap<>();

    private boolean isRedeploy;

    // Docker compose command
    private String[] dockerComposeCmd;

    // Database
    private String dbHost;
    private int dbPort = 5432;
    private String dbName;
    private String dbUsername;
    private String dbPassword;

    // State
    private Path extractedPath;
    private Path composePath;
    private boolean hasUserCompose;
    private Path frontendDistPath;
    private Path backendDistPath;
    private String networkName;

    // Output
    private Map<String, String> finalEnvVars = new HashMap<>();
    private Map<String, String> frontendBuildArgs = new HashMap<>();

    // Logs
    private List<String> logs = new ArrayList<>();
    private java.util.function.Consumer<String> logListener;

    public void addLog(String message) {
        log.info("[{}] {}", projectSlug, message);
        logs.add(message);
        if (logListener != null) {
            logListener.accept(message);
        }
    }

    public static DeploymentContext fromProject(Project project, byte[] artifactData, Map<String, String> inputEnvVars,
            boolean isRedeploy) {
        DeploymentContext ctx = new DeploymentContext();
        ctx.setProjectSlug(project.getSlug());
        ctx.setArtifactData(artifactData);

        ctx.setDefaultFrontendURL(project.getDefaultFrontendUrl());
        ctx.setCustomFrontendURL(project.getCustomFrontendUrl());
        ctx.setDefaultFrontendActive(project.isDefaultFrontendActive());
        ctx.setCustomFrontendActive(project.isCustomFrontendActive());

        ctx.setDefaultBackendURL(project.getDefaultBackendUrl());
        ctx.setCustomBackendURL(project.getCustomBackendUrl());
        ctx.setDefaultBackendActive(project.isDefaultBackendActive());
        ctx.setCustomBackendActive(project.isCustomBackendActive());

        ctx.setDefaultMonitoringURL(project.getDefaultMonitoringUrl());
        ctx.setCustomMonitoringURL(project.getCustomMonitoringUrl());
        ctx.setDefaultMonitoringActive(project.isDefaultMonitoringActive());
        ctx.setCustomMonitoringActive(project.isCustomMonitoringActive());
        ctx.setNeedMonitoringExposed(project.isNeedMonitoringExposed());

        ctx.setFrontendListeningPort(project.getFrontendListeningPort() != null ? project.getFrontendListeningPort() : 3000);
        ctx.setBackendListeningPort(project.getBackendListeningPort() != null ? project.getBackendListeningPort() : 7776);

        if (project.getEnvVars() != null) {
            ctx.setExistingEnvVars(new HashMap<>(project.getEnvVars()));
        }
        if (inputEnvVars != null) {
            ctx.setInputEnvVars(new HashMap<>(inputEnvVars));
        }

        ctx.setRedeploy(isRedeploy);

        return ctx;
    }
}
