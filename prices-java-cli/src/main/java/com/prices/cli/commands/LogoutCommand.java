package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "logout", description = "Log out from the platform")
public class LogoutCommand implements Callable<Integer> {

    @Option(names = {"--json"}, description = "Output in JSON format")
    private boolean jsonOutput;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        try {
            new ConfigManager().clearToken();
            
            if (jsonOutput) {
                ApiResponse<Void> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Logged out successfully");
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.out.println("Logged out successfully.");
            }
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                ApiResponse<Void> response = new ApiResponse<>();
                response.setSuccess(false);
                response.setMessage("Logout failed: " + e.getMessage());
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.err.println("Logout failed: " + e.getMessage());
            }
            return 1;
        }
    }
}
