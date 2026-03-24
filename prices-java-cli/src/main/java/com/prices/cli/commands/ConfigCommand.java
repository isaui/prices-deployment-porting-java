package com.prices.cli.commands;

import com.prices.cli.config.ConfigManager;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "config", description = "Show current configuration")
public class ConfigCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        ConfigManager config = new ConfigManager();
        
        System.out.println("Current Configuration:");
        System.out.println("  API URL:        " + config.getApiUrl());
        System.out.println("  Domain:         " + config.getDomain());
        System.out.println("  Deploy Script:  " + config.getSshDeployScript());
        System.out.println("  Remote Tmp:     " + config.getSshRemoteTmp());
        
        String token = config.getToken();
        System.out.println("  Token:          " + (token != null && !token.isEmpty() ? "(set)" : "(not set)"));
        
        return 0;
    }
}
