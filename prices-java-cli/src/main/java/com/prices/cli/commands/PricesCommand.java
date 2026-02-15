package com.prices.cli.commands;

import com.prices.cli.config.ConfigManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

import java.io.IOException;

@Command(
    name = "prices",
    description = "Prices CLI - Deploy and manage your WinVMJ applications.",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        // Auth
        LoginCommand.class,
        RegisterCommand.class,
        LogoutCommand.class,
        
        // Projects
        ProjectsCommand.class,
        ProjectCommand.class,
        CreateCommand.class,
        UpdateCommand.class,
        DeleteCommand.class,
        
        // Deployment
        DeployCommand.class,
        StatusCommand.class,
        HistoryCommand.class,
        LogsCommand.class,
        
        // Env Vars
        EnvVarsCommand.class,
        ConfigCommand.class
    }
)
public class PricesCommand implements Runnable {

    @Option(names = {"-a", "--api"}, description = "API server URL", scope = ScopeType.INHERIT)
    private String apiUrl;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output", scope = ScopeType.INHERIT)
    private boolean verbose;

    @Override
    public void run() {
        if (apiUrl != null && !apiUrl.isEmpty()) {
            try {
                new ConfigManager().setApiUrl(apiUrl);
            } catch (IOException e) {
                System.err.println("Failed to set API URL: " + e.getMessage());
            }
        }
        new CommandLine(this).usage(System.out);
    }
}
