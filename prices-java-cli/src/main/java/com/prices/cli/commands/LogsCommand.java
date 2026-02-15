package com.prices.cli.commands;

import com.prices.cli.api.Client;
import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "logs", description = "View project logs")
public class LogsCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project slug")
    private String slug;

    @Option(names = {"-f", "--follow"}, description = "Follow log stream")
    private boolean follow;

    @Option(names = {"-n", "--lines"}, description = "Number of lines to show", defaultValue = "100")
    private int lines;

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
            // Fetch recent logs first
            String logs = client.getProjectLogs(slug, lines);
            if (logs != null && !logs.isEmpty()) {
                System.out.println(logs);
            }

            if (follow) {
                // Stream new logs
                client.streamProjectLogs(slug, System.out::println);
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to get logs: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
