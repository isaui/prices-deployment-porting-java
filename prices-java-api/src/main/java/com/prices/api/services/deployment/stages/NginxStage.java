package com.prices.api.services.deployment.stages;

import com.prices.api.constants.Constants;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NginxStage implements PipelineStage {

    @Override
    public String name() {
        return "Nginx Configure";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        // 1. Ensure nginx config directory exists
        Path configDir = Paths.get(Constants.NGINX_CONFIG_DIR);
        Files.createDirectories(configDir);

        // 2. Generate nginx config
        String configFileName = ctx.getProjectSlug() + ".conf";
        Path configPath = configDir.resolve(configFileName);
        String config = generateConfig(ctx);

        // 3. Write config file
        Files.writeString(configPath, config);
        ctx.addLog("Generated nginx config: " + configPath);

        // 4. Test nginx config
        try {
            executeDockerExec("nginx", "-t");
            ctx.addLog("Nginx config test passed");
        } catch (Exception e) {
            Files.deleteIfExists(configPath);
            throw new RuntimeException("nginx config test failed: " + e.getMessage());
        }

        // 5. Reload nginx
        try {
            executeDockerExec("nginx", "-s", "reload");
            ctx.addLog("Nginx reloaded successfully");
        } catch (Exception e) {
            throw new RuntimeException("nginx reload failed: " + e.getMessage());
        }

        // Log active URLs
        logUrls(ctx);
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        String configFileName = ctx.getProjectSlug() + ".conf";
        Path configPath = Paths.get(Constants.NGINX_CONFIG_DIR, configFileName);

        try {
            Files.deleteIfExists(configPath);
            ctx.addLog("Removed nginx config: " + configPath);
            executeDockerExec("nginx", "-s", "reload");
            ctx.addLog("Nginx reloaded");
        } catch (Exception e) {
            ctx.addLog("Warning: failed to remove nginx config or reload: " + e.getMessage());
        }
    }

    private String generateConfig(DeploymentContext ctx) {
        StringBuilder sb = new StringBuilder();

        String frontendContainer = NamingUtils.containerName("frontend", ctx.getProjectSlug());
        String backendContainer = NamingUtils.containerName("backend", ctx.getProjectSlug());

        // Frontend
        if (hasFrontend(ctx)) {
            sb.append("# Frontend server\n");
            sb.append("server {\n");
            sb.append("    listen 80;\n");
            sb.append("    server_name ").append(getFrontendServerNames(ctx)).append(";\n\n");
            sb.append("    resolver 127.0.0.11 valid=30s;\n");
            sb.append("    set $frontend_upstream http://").append(frontendContainer).append(":80;\n\n");
            sb.append("    location / {\n");
            sb.append("        proxy_pass $frontend_upstream;\n");
            sb.append("        proxy_http_version 1.1;\n");
            sb.append("        proxy_set_header Host $host;\n");
            sb.append("        proxy_set_header X-Real-IP $remote_addr;\n");
            sb.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
            sb.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");
            sb.append("        proxy_set_header Upgrade $http_upgrade;\n");
            sb.append("        proxy_set_header Connection \"upgrade\";\n");
            sb.append("        proxy_read_timeout 86400;\n");
            sb.append("    }\n");
            sb.append("}\n");
        }

        // Backend
        if (hasBackend(ctx)) {
            sb.append("# Backend server\n");
            sb.append("server {\n");
            sb.append("    listen 80;\n");
            sb.append("    server_name ").append(getBackendServerNames(ctx)).append(";\n\n");
            sb.append("    resolver 127.0.0.11 valid=30s;\n");
            sb.append("    set $backend_upstream http://").append(backendContainer).append(":7776;\n\n");
            sb.append("    location / {\n");
            sb.append("        proxy_pass $backend_upstream;\n");
            sb.append("        proxy_http_version 1.1;\n");
            sb.append("        proxy_set_header Host $host;\n");
            sb.append("        proxy_set_header X-Real-IP $remote_addr;\n");
            sb.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
            sb.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");
            sb.append("        proxy_set_header Upgrade $http_upgrade;\n");
            sb.append("        proxy_set_header Connection \"upgrade\";\n");
            sb.append("        proxy_read_timeout 86400;\n");
            sb.append("    }\n");
            sb.append("}\n");
        }

        // Monitoring
        if (hasMonitoring(ctx)) {
            sb.append("# Monitoring server\n");
            sb.append("server {\n");
            sb.append("    listen 80;\n");
            sb.append("    server_name ").append(getMonitoringServerNames(ctx)).append(";\n\n");
            sb.append("    resolver 127.0.0.11 valid=30s;\n");
            sb.append("    set $monitoring_upstream http://").append(backendContainer).append(":9464;\n\n");
            sb.append("    location / {\n");
            sb.append("        proxy_pass $monitoring_upstream;\n");
            sb.append("        proxy_http_version 1.1;\n");
            sb.append("        proxy_set_header Host $host;\n");
            sb.append("        proxy_set_header X-Real-IP $remote_addr;\n");
            sb.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
            sb.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");
            sb.append("        proxy_set_header Upgrade $http_upgrade;\n");
            sb.append("        proxy_set_header Connection \"upgrade\";\n");
            sb.append("        proxy_read_timeout 86400;\n");
            sb.append("    }\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    private void executeDockerExec(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add(Constants.NGINX_CONTAINER_NAME);
        for (String arg : args)
            cmd.add(arg);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                output.append(line).append("\n");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + output.toString());
        }
    }

    private boolean hasFrontend(DeploymentContext ctx) {
        return (ctx.isDefaultFrontendActive() && ctx.getDefaultFrontendURL() != null) ||
                (ctx.isCustomFrontendActive() && ctx.getCustomFrontendURL() != null);
    }

    private boolean hasBackend(DeploymentContext ctx) {
        return (ctx.isDefaultBackendActive() && ctx.getDefaultBackendURL() != null) ||
                (ctx.isCustomBackendActive() && ctx.getCustomBackendURL() != null);
    }

    private boolean hasMonitoring(DeploymentContext ctx) {
        if (!ctx.isNeedMonitoringExposed())
            return false;
        return (ctx.isDefaultMonitoringActive() && ctx.getDefaultMonitoringURL() != null) ||
                (ctx.isCustomMonitoringActive() && ctx.getCustomMonitoringURL() != null);
    }

    private String getFrontendServerNames(DeploymentContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx.isDefaultFrontendActive() && ctx.getDefaultFrontendURL() != null)
            names.add(ctx.getDefaultFrontendURL());
        if (ctx.isCustomFrontendActive() && ctx.getCustomFrontendURL() != null)
            names.add(ctx.getCustomFrontendURL());
        return String.join(" ", names);
    }

    private String getBackendServerNames(DeploymentContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx.isDefaultBackendActive() && ctx.getDefaultBackendURL() != null)
            names.add(ctx.getDefaultBackendURL());
        if (ctx.isCustomBackendActive() && ctx.getCustomBackendURL() != null)
            names.add(ctx.getCustomBackendURL());
        return String.join(" ", names);
    }

    private String getMonitoringServerNames(DeploymentContext ctx) {
        List<String> names = new ArrayList<>();
        if (ctx.isDefaultMonitoringActive() && ctx.getDefaultMonitoringURL() != null)
            names.add(ctx.getDefaultMonitoringURL());
        if (ctx.isCustomMonitoringActive() && ctx.getCustomMonitoringURL() != null)
            names.add(ctx.getCustomMonitoringURL());
        return String.join(" ", names);
    }

    private void logUrls(DeploymentContext ctx) {
        if (hasFrontend(ctx))
            ctx.addLog("Frontend URL: " + getFrontendServerNames(ctx));
        if (hasBackend(ctx))
            ctx.addLog("Backend URL: " + getBackendServerNames(ctx));
        if (hasMonitoring(ctx))
            ctx.addLog("Monitoring URL: " + getMonitoringServerNames(ctx));
    }
}
