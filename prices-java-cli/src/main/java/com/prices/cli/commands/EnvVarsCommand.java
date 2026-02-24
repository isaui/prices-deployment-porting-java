package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "env", description = "Manage environment variables")
public class EnvVarsCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project slug")
    private String slug;

    @Option(names = {"--set"}, description = "Set env vars (KEY=VALUE)")
    private Map<String, String> setVars;

    @Option(names = {"--replace"}, description = "Replace all env vars (KEY=VALUE)")
    private Map<String, String> replaceVars;

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
            if (replaceVars != null && !replaceVars.isEmpty()) {
                client.replaceEnvVars(slug, replaceVars);
                System.out.println("Environment variables replaced successfully.");
            } else if (setVars != null && !setVars.isEmpty()) {
                client.updateEnvVars(slug, setVars);
                System.out.println("Environment variables updated successfully.");
            }
            
            // Always show current vars after operation or if no op specified
            Map<String, String> current = client.getEnvVars(slug);
            
            if (jsonOutput) {
                ApiResponse<Map<String, String>> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Environment variables retrieved successfully");
                response.setData(current);
                System.out.println(objectMapper.writeValueAsString(response));
                return 0;
            }
            
            if (current.isEmpty()) {
                System.out.println("No environment variables set.");
            } else {
                System.out.println("Environment variables for " + slug + ":");
                current.forEach((k, v) -> System.out.printf("  %s=%s%n", k, v));
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to manage env vars: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
