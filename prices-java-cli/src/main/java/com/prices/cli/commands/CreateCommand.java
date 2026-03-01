package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.api.models.CreateProjectRequest;
import com.prices.cli.api.models.Project;
import com.prices.cli.config.ConfigManager;
import com.prices.cli.util.UrlUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "create", description = "Create a new project")
public class CreateCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Option(names = {"-n", "--name"}, description = "Project name")
    private String name;

    @Option(names = {"-d", "--desc"}, description = "Description")
    private String description;

    @Option(names = {"--frontend-url"}, description = "Custom frontend domain")
    private String customFrontendUrl;

    @Option(names = {"--backend-url"}, description = "Custom backend domain")
    private String customBackendUrl;

    @Option(names = {"--monitoring-url"}, description = "Custom monitoring domain")
    private String customMonitoringUrl;

    @Option(names = {"--monitoring"}, description = "Expose monitoring endpoint")
    private boolean exposeMonitoring;

    @Option(names = {"--frontend-port"}, description = "Frontend listening port (default: 80)")
    private Integer frontendPort;

    @Option(names = {"--backend-port"}, description = "Backend listening port (default: 7776)")
    private Integer backendPort;

    @Option(names = {"--json"}, description = "Output result as JSON")
    private boolean jsonOutput;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        if (name == null && !jsonOutput) {
            System.out.print("Project Name: ");
            name = System.console().readLine();
        } else if (name == null && jsonOutput) {
            outputJsonError("Project name is required");
            return 1;
        }

        CreateProjectRequest req = new CreateProjectRequest();
        req.setName(name);
        req.setDescription(description);
        req.setCustomFrontendURL(customFrontendUrl);
        req.setCustomBackendURL(customBackendUrl);
        req.setCustomMonitoringURL(customMonitoringUrl);
        req.setNeedMonitoringExposed(exposeMonitoring);
        req.setFrontendListeningPort(frontendPort);
        req.setBackendListeningPort(backendPort);

        try {
            Project project = client.createProject(req);
            
            if (jsonOutput) {
                outputJsonSuccess("Project created successfully", project);
            } else {
                System.out.println("Project created successfully: " + project.getSlug());
                
                if (project.isDefaultFrontendActive() && project.getDefaultFrontendURL() != null) {
                    System.out.println("Frontend: " + UrlUtil.fullUrl(project.getDefaultFrontendURL()));
                }
                if (project.isDefaultBackendActive() && project.getDefaultBackendURL() != null) {
                    System.out.println("Backend:  " + UrlUtil.fullUrl(project.getDefaultBackendURL()));
                }
            }
            
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                outputJsonError(e.getMessage());
            } else {
                System.err.println("Failed to create project: " + e.getMessage());
                if (parent.isVerbose()) {
                    e.printStackTrace();
                }
            }
            return 1;
        }
    }

    private void outputJsonSuccess(String message, Project project) throws Exception {
        ApiResponse<Project> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(project);
        System.out.println(objectMapper.writeValueAsString(response));
    }

    private void outputJsonError(String message) throws Exception {
        ApiResponse<?> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        System.out.println(objectMapper.writeValueAsString(response));
    }
}
