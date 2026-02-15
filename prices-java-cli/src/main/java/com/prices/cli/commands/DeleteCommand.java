package com.prices.cli.commands;

import com.prices.cli.api.Client;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "delete", description = "Delete a project")
public class DeleteCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project slug")
    private String slug;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        String token = configManager.getToken();
        
        if (token == null || token.isEmpty()) {
            System.err.println("Not logged in. Run 'prices login' first.");
            return 1;
        }

        if (!skipConfirm) {
            System.out.printf("Are you sure you want to delete project '%s'? This cannot be undone. (y/N): ", slug);
            String input = System.console().readLine();
            if (input == null || (!input.equalsIgnoreCase("y") && !input.equalsIgnoreCase("yes"))) {
                System.out.println("Aborted.");
                return 0;
            }
        }

        Client client = new Client(configManager.getApiUrl());
        client.setToken(token);

        try {
            client.deleteProject(slug);
            System.out.println("Project deleted successfully.");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to delete project: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
