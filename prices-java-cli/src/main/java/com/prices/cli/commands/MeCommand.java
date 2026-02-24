package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.api.models.User;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "me",
    description = "Get current user information"
)
public class MeCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    private PricesCommand parent;

    @CommandLine.Option(
        names = {"--json"},
        description = "Output in JSON format"
    )
    private boolean jsonOutput;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        try {
            ConfigManager configManager = new ConfigManager();
            String token = configManager.getToken();
            
            if (token == null) {
                System.err.println("Not logged in. Please run 'prices login' first.");
                return 1;
            }
            
            Client client = new Client(configManager.getApiUrl());
            client.setToken(token);
            
            User user = client.getCurrentUser();
            
            if (jsonOutput) {
                ApiResponse<User> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Current user");
                response.setData(user);
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.out.println("Current User Information:");
                System.out.println("  ID: " + user.getId());
                System.out.println("  Username: " + user.getUsername());
                System.out.println("  Email: " + user.getEmail());
                System.out.println("  Role: " + user.getRole());
            }
            
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                ApiResponse<?> errorResponse = new ApiResponse<>();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("Failed to get user info: " + e.getMessage());
                System.out.println(objectMapper.writeValueAsString(errorResponse));
            } else {
                System.err.println("Error: " + e.getMessage());
                if (parent.isVerbose()) {
                    e.printStackTrace();
                }
            }
            return 1;
        }
    }
}
