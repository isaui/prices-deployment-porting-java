package com.prices.cli.commands;

import com.prices.cli.api.Client;
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

    @Option(names = {"-p", "--password"}, description = "Password", interactive = true)
    private String password;

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        Client client = new Client(configManager.getApiUrl());

        if (username == null) {
            System.out.print("Username: ");
            username = System.console().readLine();
        }

        if (email == null) {
            System.out.print("Email: ");
            email = System.console().readLine();
        }

        if (password == null) {
            System.out.print("Password: ");
            char[] pass = System.console().readPassword();
            password = new String(pass);
        }

        try {
            String token = client.register(username, email, password);
            configManager.saveToken(token);
            System.out.println("Registration successful! You are now logged in.");
            return 0;
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
