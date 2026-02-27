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

@Command(name = "register", description = "Create a new account")
public class RegisterCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Option(names = {"-u", "--username"}, description = "Username")
    private String username;

    @Option(names = {"-e", "--email"}, description = "Email")
    private String email;

    @Option(names = {"-p", "--password"}, description = "Password")
    private String password;

    @Option(names = {"--json"}, description = "Output in JSON format (non-interactive, requires -u, -e, -p)")
    private boolean jsonOutput;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        Client client = new Client(configManager.getApiUrl());

        // JSON mode requires all flags
        if (jsonOutput) {
            if (username == null || email == null || password == null) {
                ApiResponse<?> errorResponse = new ApiResponse<>();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("JSON mode requires -u, -e, and -p flags");
                System.out.println(objectMapper.writeValueAsString(errorResponse));
                return 1;
            }
        } else {
            // Interactive mode
            java.io.Console console = System.console();
            java.util.Scanner scanner = console == null ? new java.util.Scanner(System.in) : null;

            if (username == null) {
                System.out.print("Username: ");
                if (console != null) {
                    username = console.readLine();
                } else if (scanner != null) {
                    username = scanner.nextLine();
                } else {
                    System.err.println("Error: No console available. Use -u, -e, -p flags.");
                    return 1;
                }
            }

            if (email == null) {
                System.out.print("Email: ");
                if (console != null) {
                    email = console.readLine();
                } else if (scanner != null) {
                    email = scanner.nextLine();
                } else {
                    System.err.println("Error: No console available. Use -u, -e, -p flags.");
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
                    System.err.println("Error: No console available. Use -u, -e, -p flags.");
                    return 1;
                }
            }
        }

        try {
            String token = client.register(username, email, password);
            configManager.saveToken(token);
            
            if (jsonOutput) {
                // Return success with registered user info (no need to call /me endpoint)
                User user = new User();
                user.setUsername(username);
                user.setEmail(email);
                
                ApiResponse<User> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Registration successful");
                response.setData(user);
                System.out.println(objectMapper.writeValueAsString(response));
            } else {
                System.out.println("Registration successful!");
            }
            return 0;
        } catch (Exception e) {
            if (jsonOutput) {
                ApiResponse<?> errorResponse = new ApiResponse<>();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("Registration failed: " + e.getMessage());
                System.out.println(objectMapper.writeValueAsString(errorResponse));
            } else {
                System.err.println("Registration failed: " + e.getMessage());
                if (parent.isVerbose()) {
                    e.printStackTrace();
                }
            }
            return 1;
        }
    }
}
