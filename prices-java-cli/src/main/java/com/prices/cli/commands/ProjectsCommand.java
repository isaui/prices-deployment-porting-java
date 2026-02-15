package com.prices.cli.commands;

import com.prices.cli.api.Client;
import com.prices.cli.api.models.Project;
import com.prices.cli.config.ConfigManager;
import com.prices.cli.util.UrlUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "projects", description = "List all projects")
public class ProjectsCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

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
            List<Project> projects = client.listProjects();
            
            if (projects.isEmpty()) {
                System.out.println("No projects found.");
                System.out.println("\nCreate your first project with:");
                System.out.println("  prices create       - Create a new project");
                System.out.println("  prices deploy .     - Deploy current directory");
                return 0;
            }

            System.out.printf("%-20s %-15s %-30s %-30s%n", "SLUG", "STATUS", "FRONTEND", "BACKEND");
            System.out.println("----------------------------------------------------------------------------------------------------");
            
            for (Project p : projects) {
                String frontend = "-";
                if (p.isCustomFrontendActive() && p.getCustomFrontendURL() != null) {
                    frontend = UrlUtil.fullUrl(p.getCustomFrontendURL());
                } else if (p.isDefaultFrontendActive() && p.getDefaultFrontendURL() != null) {
                    frontend = UrlUtil.fullUrl(p.getDefaultFrontendURL());
                }
                
                String backend = "-";
                if (p.isCustomBackendActive() && p.getCustomBackendURL() != null) {
                    backend = UrlUtil.fullUrl(p.getCustomBackendURL());
                } else if (p.isDefaultBackendActive() && p.getDefaultBackendURL() != null) {
                    backend = UrlUtil.fullUrl(p.getDefaultBackendURL());
                }

                System.out.printf("%-20s %-15s %-30s %-30s%n", 
                    p.getSlug(), 
                    p.getStatus(), 
                    truncate(frontend, 30), 
                    truncate(backend, 30)
                );
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to list projects: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
    
    private String truncate(String s, int len) {
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }
}
