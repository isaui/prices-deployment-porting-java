package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SummaryStage implements PipelineStage {

    @Override
    public String name() {
        return "Summary";
    }

    @Override
    public void execute(DeploymentContext ctx) {
        ctx.addLog("");
        ctx.addLog("===========================================");
        ctx.addLog("  DEPLOYMENT SUMMARY");
        ctx.addLog("===========================================");
        ctx.addLog("  Project: " + ctx.getProjectSlug());
        ctx.addLog("");
        
        // Frontend URLs
        ctx.addLog("  Frontend:");
        if (ctx.isDefaultFrontendActive() && ctx.getDefaultFrontendURL() != null) {
            ctx.addLog("    Default: " + NamingUtils.fullURL(ctx.getDefaultFrontendURL()));
        }
        if (ctx.isCustomFrontendActive() && ctx.getCustomFrontendURL() != null) {
            ctx.addLog("    Custom:  " + NamingUtils.fullURL(ctx.getCustomFrontendURL()));
        }
        
        // Backend URLs
        ctx.addLog("  Backend:");
        if (ctx.isDefaultBackendActive() && ctx.getDefaultBackendURL() != null) {
            ctx.addLog("    Default: " + NamingUtils.fullURL(ctx.getDefaultBackendURL()));
        }
        if (ctx.isCustomBackendActive() && ctx.getCustomBackendURL() != null) {
            ctx.addLog("    Custom:  " + NamingUtils.fullURL(ctx.getCustomBackendURL()));
        }
        
        // Monitoring URLs (if exposed)
        if (ctx.isNeedMonitoringExposed()) {
            ctx.addLog("  Monitoring:");
            if (ctx.isDefaultMonitoringActive() && ctx.getDefaultMonitoringURL() != null) {
                ctx.addLog("    Default: " + NamingUtils.fullURL(ctx.getDefaultMonitoringURL()));
            }
            if (ctx.isCustomMonitoringActive() && ctx.getCustomMonitoringURL() != null) {
                ctx.addLog("    Custom:  " + NamingUtils.fullURL(ctx.getCustomMonitoringURL()));
            }
        }
        
        ctx.addLog("");
        ctx.addLog("===========================================");
    }
}
