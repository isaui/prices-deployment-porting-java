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

@Command(name = "project", description = "Show project details")
public class ProjectCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project slug")
    private String slug;

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
            Project project = client.getProject(slug);
            
            if (jsonOutput) {
                ApiResponse<Project> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Project retrieved successfully");
                response.setData(project);
                System.out.println(objectMapper.writeValueAsString(response));
                return 0;
            }
            
            System.out.println("Project: " + project.getName() + " (" + project.getSlug() + ")");
            System.out.println("ID: " + project.getId());
            System.out.println("Status: " + project.getStatus());
            System.out.println("Description: " + (project.getDescription() != null ? project.getDescription() : "-"));
            System.out.println("Created At: " + project.getCreatedAt());
            System.out.println();
            
            System.out.println("Frontend:");
            System.out.println("  Default: " + UrlUtil.formatUrl(project.getDefaultFrontendURL(), project.isDefaultFrontendActive()));
            System.out.println("  Custom:  " + UrlUtil.formatUrl(project.getCustomFrontendURL(), project.isCustomFrontendActive()));
            System.out.println("  Active:  " + UrlUtil.bestUrl(
                project.getCustomFrontendURL(), project.isCustomFrontendActive(),
                project.getDefaultFrontendURL(), project.isDefaultFrontendActive()
            ));
            System.out.println();
            
            System.out.println("Backend:");
            System.out.println("  Default: " + UrlUtil.formatUrl(project.getDefaultBackendURL(), project.isDefaultBackendActive()));
            System.out.println("  Custom:  " + UrlUtil.formatUrl(project.getCustomBackendURL(), project.isCustomBackendActive()));
            System.out.println("  Active:  " + UrlUtil.bestUrl(
                project.getCustomBackendURL(), project.isCustomBackendActive(),
                project.getDefaultBackendURL(), project.isDefaultBackendActive()
            ));
            System.out.println();
            
            if (project.isNeedMonitoringExposed()) {
                System.out.println("Monitoring:");
                System.out.println("  Default: " + UrlUtil.formatUrl(project.getDefaultMonitoringURL(), project.isDefaultMonitoringActive()));
                System.out.println("  Custom:  " + UrlUtil.formatUrl(project.getCustomMonitoringURL(), project.isCustomMonitoringActive()));
                System.out.println("  Active:  " + UrlUtil.bestUrl(
                    project.getCustomMonitoringURL(), project.isCustomMonitoringActive(),
                    project.getDefaultMonitoringURL(), project.isDefaultMonitoringActive()
                ));
            } else {
                System.out.println("Monitoring: Disabled");
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get project: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
