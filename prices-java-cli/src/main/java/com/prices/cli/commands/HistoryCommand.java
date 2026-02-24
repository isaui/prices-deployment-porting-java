package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.Deployment;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "history", description = "Show deployment history")
public class HistoryCommand implements Callable<Integer> {

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
            List<Deployment> history = client.getDeploymentHistory(slug);
            
            if (jsonOutput) {
                System.out.println(objectMapper.writeValueAsString(history));
                return 0;
            }
            
            if (history.isEmpty()) {
                System.out.println("No deployments found for " + slug);
                return 0;
            }

            System.out.printf("Deployment history for %s:%n", slug);
            System.out.printf("%-10s %-15s %-15s %-25s %s%n", "ID", "STATUS", "DURATION", "CREATED AT", "ERROR");
            System.out.println("------------------------------------------------------------------------------------------");
            
            for (Deployment d : history) {
                System.out.printf("%-10d %-15s %-15s %-25s %s%n", 
                    d.getId(), 
                    d.getStatus(), 
                    d.getDuration(), 
                    d.getCreatedAt(),
                    d.getError() != null ? d.getError() : ""
                );
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get history: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
