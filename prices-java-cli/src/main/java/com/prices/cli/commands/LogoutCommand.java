package com.prices.cli.commands;

import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "logout", description = "Log out from the platform")
public class LogoutCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        new ConfigManager().clearToken();
        System.out.println("Logged out successfully.");
        return 0;
    }
}
