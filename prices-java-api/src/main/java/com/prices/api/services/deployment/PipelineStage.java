package com.prices.api.services.deployment;

public interface PipelineStage {
    String name();

    void execute(DeploymentContext ctx) throws Exception;

    void rollback(DeploymentContext ctx);
}
