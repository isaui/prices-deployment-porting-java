package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
public class ParseMonitoringConfigStage implements PipelineStage {

    @Override
    public String name() {
        return "Parse Monitoring Config";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        Path backendPath = ctx.getExtractedPath().resolve("backend");
        Path monPropsPath = backendPath.resolve("monitoring.properties");

        if (!Files.exists(monPropsPath)) {
            ctx.addLog("No monitoring.properties found, skipping");
            return;
        }

        Properties props = new Properties();
        try (var in = Files.newInputStream(monPropsPath)) {
            props.load(in);
        }

        boolean enabled = Boolean.parseBoolean(props.getProperty("monitoring.enabled", "false"));
        String featuresStr = props.getProperty("monitoring.features", "");
        List<String> features = Arrays.stream(featuresStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        ctx.setMonitoringEnabled(enabled);
        ctx.setMonitoringFeatures(features);

        ctx.addLog(String.format("Monitoring config: enabled=%s, features=%s", enabled, features));
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Nothing to rollback — only sets ctx fields
    }
}
