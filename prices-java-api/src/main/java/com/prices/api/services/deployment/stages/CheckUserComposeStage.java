package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class CheckUserComposeStage implements PipelineStage {

    @Override
    public String name() {
        return "Check User Compose";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        Path composePath = ctx.getExtractedPath().resolve("docker-compose.yml");
        if (Files.exists(composePath)) {
            ctx.addLog("User docker-compose.yml found, skipping dist preparation");
            ctx.setHasUserCompose(true);
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // no-op
    }
}
