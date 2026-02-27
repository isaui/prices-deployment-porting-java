package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.api.models.Project;
import com.prices.cli.api.models.UpdateProjectRequest;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "update", description = "Update project settings")
public class UpdateCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project slug")
    private String slug;

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
    private Boolean exposeMonitoring;

    @Option(names = {"--frontend-port"}, description = "Frontend listening port")
    private Integer frontendPort;

    @Option(names = {"--backend-port"}, description = "Backend listening port")
    private Integer backendPort;

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

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName(name);
        req.setDescription(description);
        req.setCustomFrontendURL(customFrontendUrl);
        req.setCustomBackendURL(customBackendUrl);
        req.setCustomMonitoringURL(customMonitoringUrl);
        req.setNeedMonitoringExposed(exposeMonitoring);
        req.setFrontendListeningPort(frontendPort);
        req.setBackendListeningPort(backendPort);

        try {
            Project project = client.updateProject(slug, req);
            
            if (jsonOutput) {
                ApiResponse<Project> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Project updated successfully");
                response.setData(project);
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.out.println("Project updated successfully: " + project.getSlug());
            }
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                ApiResponse<Object> response = new ApiResponse<>();
                response.setSuccess(false);
                response.setMessage("Failed to update project: " + e.getMessage());
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.err.println("Failed to update project: " + e.getMessage());
                if (parent.isVerbose()) {
                    e.printStackTrace();
                }
            }
            return 1;
        }
    }
}
