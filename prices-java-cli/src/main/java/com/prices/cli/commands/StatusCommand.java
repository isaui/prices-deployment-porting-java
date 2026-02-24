package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.api.models.Project;
import com.prices.cli.config.ConfigManager;
import com.prices.cli.util.UrlUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "status", description = "Check project status")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project Slug")
    private String projectSlug;

    @Option(names = {"--json", "-j"}, description = "Output in JSON format")
    private boolean jsonOutput;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        String token = configManager.getToken();

        if (token == null || token.isEmpty()) {
            System.err.println("Not logged in. Run 'prices login' first.");
            return 1;
        }

        Client client = new Client(configManager.getApiUrl());
        client.setToken(token);

        try {
            Project project = client.getProject(projectSlug);
            
            if (jsonOutput) {
                ApiResponse<Project> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Project status retrieved successfully");
                response.setData(project);
                System.out.println(objectMapper.writeValueAsString(response));
                return 0;
            }

            System.out.println("Project: " + project.getName());
            System.out.println("Slug: " + project.getSlug());
            System.out.println("Status: " + project.getStatus());

            if (project.getDefaultFrontendURL() != null && !project.getDefaultFrontendURL().isEmpty()
                    && project.isDefaultFrontendActive()) {
                System.out.println("Frontend: " + UrlUtil.fullUrl(project.getDefaultFrontendURL()));
            }
            if (project.getDefaultBackendURL() != null && !project.getDefaultBackendURL().isEmpty()
                    && project.isDefaultBackendActive()) {
                System.out.println("Backend: " + UrlUtil.fullUrl(project.getDefaultBackendURL()));
            }
            if (project.isNeedMonitoringExposed() && project.getDefaultMonitoringURL() != null
                    && !project.getDefaultMonitoringURL().isEmpty()) {
                System.out.println("Monitoring: " + UrlUtil.fullUrl(project.getDefaultMonitoringURL()));
            }
            System.out.println("Updated: " + project.getUpdatedAt());

            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get project status: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
