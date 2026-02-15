package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DockerRunStage implements PipelineStage {

    private final String[] dockerComposeCmd;

    public DockerRunStage(String[] dockerComposeCmd) {
        this.dockerComposeCmd = dockerComposeCmd;
    }

    @Override
    public String name() {
        return "Docker Run";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        String projectName = NamingUtils.projectName(ctx.getProjectSlug());

        // 1. Stop existing containers if any
        ctx.addLog("Stopping existing containers if any...");
        List<String> downArgs = new ArrayList<>();
        downArgs.add("-f");
        downArgs.add(ctx.getComposePath().toString());
        downArgs.add("-p");
        downArgs.add(projectName);
        downArgs.add("down");

        if (!ctx.isRedeploy()) {
            downArgs.add("-v");
        }

        executeDockerCompose(ctx, downArgs, true); // Ignore error

        // 2. Build and start containers
        ctx.addLog("Building and starting containers from: " + ctx.getComposePath());

        List<String> upArgs = new ArrayList<>();
        upArgs.add("-f");
        upArgs.add(ctx.getComposePath().toString());
        upArgs.add("-p");
        upArgs.add(projectName);
        upArgs.add("up");
        upArgs.add("-d");
        upArgs.add("--build");

        executeDockerCompose(ctx, upArgs, false);
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        if (ctx.getComposePath() == null)
            return;

        String projectName = NamingUtils.projectName(ctx.getProjectSlug());
        ctx.addLog("Stopping containers: " + projectName);

        List<String> args = new ArrayList<>();
        args.add("-f");
        args.add(ctx.getComposePath().toString());
        args.add("-p");
        args.add(projectName);
        args.add("down");

        if (!ctx.isRedeploy()) {
            args.add("-v");
        }

        try {
            executeDockerCompose(ctx, args, true);
        } catch (Exception e) {
            ctx.addLog("Warning: failed to stop containers: " + e.getMessage());
        }
    }

    private void executeDockerCompose(DeploymentContext ctx, List<String> args, boolean ignoreError) throws Exception {
        List<String> fullCmd = new ArrayList<>();
        for (String s : dockerComposeCmd)
            fullCmd.add(s);
        fullCmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        pb.directory(ctx.getExtractedPath().toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    ctx.addLog(line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && !ignoreError) {
            throw new RuntimeException("Docker compose exited with code " + exitCode);
        }
    }
}
