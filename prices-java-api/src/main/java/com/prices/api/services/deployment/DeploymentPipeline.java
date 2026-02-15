package com.prices.api.services.deployment;

import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class DeploymentPipeline {
    private final List<PipelineStage> stages;
    private final List<PipelineStage> executedStages;

    public DeploymentPipeline(List<PipelineStage> stages) {
        this.stages = stages;
        this.executedStages = new ArrayList<>();
    }

    public void execute(DeploymentContext ctx) throws Exception {
        for (PipelineStage stage : stages) {
            ctx.addLog(String.format("Starting stage: %s", stage.name()));

            try {
                stage.execute(ctx);
                executedStages.add(stage);
                ctx.addLog(String.format("Completed stage: %s", stage.name()));
            } catch (Exception e) {
                ctx.addLog(String.format("Stage %s failed: %s", stage.name(), e.getMessage()));
                throw e;
            }
        }
    }

    public void rollback(DeploymentContext ctx) {
        ctx.addLog("Starting rollback...");
        List<PipelineStage> reversed = new ArrayList<>(executedStages);
        Collections.reverse(reversed);

        for (PipelineStage stage : reversed) {
            ctx.addLog(String.format("Rolling back stage: %s", stage.name()));
            try {
                stage.rollback(ctx);
            } catch (Exception e) {
                ctx.addLog(String.format("Rollback failed for stage %s: %s", stage.name(), e.getMessage()));
            }
        }
        ctx.addLog("Rollback completed");
    }
}
