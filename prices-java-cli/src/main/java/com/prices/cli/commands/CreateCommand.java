package com.prices.cli.commands;

import com.prices.cli.api.Client;
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

    @Option(names = {"-n", "--name"}, description = "Project name", interactive = true)
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

        if (name == null) {
            System.out.print("Project Name: ");
            name = System.console().readLine();
        }

        CreateProjectRequest req = new CreateProjectRequest(
            name, description, customFrontendUrl, customBackendUrl, customMonitoringUrl, exposeMonitoring
        );

        try {
            Project project = client.createProject(req);
            System.out.println("Project created successfully: " + project.getSlug());
            
            if (project.isDefaultFrontendActive() && project.getDefaultFrontendURL() != null) {
                System.out.println("Frontend: " + UrlUtil.fullUrl(project.getDefaultFrontendURL()));
            }
            if (project.isDefaultBackendActive() && project.getDefaultBackendURL() != null) {
                System.out.println("Backend:  " + UrlUtil.fullUrl(project.getDefaultBackendURL()));
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to create project: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
