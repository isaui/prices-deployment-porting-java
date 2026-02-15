package com.prices.cli.commands;

import com.prices.cli.api.Client;
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

    @Option(names = {"-p", "--password"}, description = "Password", interactive = true)
    private String password;

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        Client client = new Client(configManager.getApiUrl());

        if (username == null) {
            System.out.print("Username/Email: ");
            username = System.console().readLine();
        }

        if (password == null) {
            System.out.print("Password: ");
            char[] pass = System.console().readPassword();
            password = new String(pass);
        }

        try {
            String token = client.login(username, password);
            configManager.saveToken(token);
            System.out.println("Login successful!");
            return 0;
        } catch (Exception e) {
            System.err.println("Login failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
