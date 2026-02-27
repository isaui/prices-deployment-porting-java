package com.prices.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.Client;
import com.prices.cli.api.models.ApiResponse;
import com.prices.cli.api.models.User;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "login", description = "Authenticate with the platform")
public class LoginCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Option(names = {"-u", "--username"}, description = "Username or email")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Password")
    private String password;

    @Option(names = {"--json"}, description = "Output in JSON format (non-interactive, requires -u and -p)")
    private boolean jsonOutput;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        Client client = new Client(configManager.getApiUrl());

        // JSON mode requires both username and password flags
        if (jsonOutput) {
            if (username == null || password == null) {
                ApiResponse<?> errorResponse = new ApiResponse<>();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("JSON mode requires both -u and -p flags");
                System.out.println(objectMapper.writeValueAsString(errorResponse));
                return 1;
            }
        } else {
            // Interactive mode - handle console input
            java.io.Console console = System.console();
            java.util.Scanner scanner = console == null ? new java.util.Scanner(System.in) : null;

            if (username == null) {
                System.out.print("Username/Email: ");
                if (console != null) {
                    username = console.readLine();
                } else if (scanner != null) {
                    username = scanner.nextLine();
                } else {
                    System.err.println("Error: No console available. Use -u and -p flags.");
                    return 1;
                }
            }

            if (password == null) {
                System.out.print("Password: ");
                if (console != null) {
                    char[] pass = console.readPassword();
                    password = new String(pass);
                } else if (scanner != null) {
                    password = scanner.nextLine();
                } else {
                    System.err.println("Error: No console available. Use -u and -p flags.");
                    return 1;
                }
            }
        }

        try {
            String token = client.login(username, password);
            configManager.saveToken(token);
            
            if (jsonOutput) {
                // Get user info after login
                client.setToken(token);
                User user = client.getCurrentUser();
                
                ApiResponse<User> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Login successful");
                response.setData(user);
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.out.println("Login successful!");
            }
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                ApiResponse<?> errorResponse = new ApiResponse<>();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("Login failed: " + e.getMessage());
                System.out.println(objectMapper.writeValueAsString(errorResponse));
            } else {
                System.err.println("Login failed: " + e.getMessage());
                if (parent.isVerbose()) {
                    e.printStackTrace();
                }
            }
            return 1;
        }
    }
}
