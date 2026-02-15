package com.prices.cli;

import com.prices.cli.commands.PricesCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new PricesCommand()).execute(args);
        System.exit(exitCode);
    }
}
