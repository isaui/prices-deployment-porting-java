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

@Command(name = "monitoring", description = "Get monitoring dashboard URL for a project")
public class MonitoringCommand implements Callable<Integer> {

    @ParentCommand
    private PricesCommand parent;

    @Parameters(index = "0", description = "Project Slug")
    private String projectSlug;

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
            Map<String, Object> data = client.createMonitoringToken(projectSlug);

            if (jsonOutput) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>();
                response.setSuccess(true);
                response.setMessage("Monitoring token created");
                response.setData(data);
                System.out.println(objectMapper.writeValueAsString(response));
                return 0;
            }

            String url = (String) data.get("url");
            String expiredAt = (String) data.get("expiredAt");

            String baseUrl = configManager.getApiUrl();
            String fullUrl = baseUrl + url;

            System.out.println("Monitoring URL: " + fullUrl);
            System.out.println("Expires: " + expiredAt);

            return 0;
        } catch (Exception e) {
            System.err.println("Failed to create monitoring token: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
