package com.prices.api.services.deployment.stages;

import com.prices.api.constants.EnvKeys;
import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnvStage implements PipelineStage {

    @Override
    public String name() {
        return "Environment Variables";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        Map<String, String> finalEnvVars = ctx.getFinalEnvVars();

        if (ctx.isRedeploy()) {
            // Redeploy: start with existing env vars, only merge input overrides
            if (ctx.getExistingEnvVars() != null && !ctx.getExistingEnvVars().isEmpty()) {
                finalEnvVars.putAll(ctx.getExistingEnvVars());
                ctx.addLog(String.format("Redeploy: loaded %d existing env vars", ctx.getExistingEnvVars().size()));
            }

            if (ctx.getInputEnvVars() != null && !ctx.getInputEnvVars().isEmpty()) {
                finalEnvVars.putAll(ctx.getInputEnvVars());
                ctx.addLog(String.format("Merged %d input env vars", ctx.getInputEnvVars().size()));
            }
        } else {
            // First deploy: generate defaults, then merge existing and input
            Map<String, String> defaults = getDefaultEnvVars(ctx);
            finalEnvVars.putAll(defaults);
            ctx.addLog(String.format("Applied %d default env vars", defaults.size()));

            if (ctx.getExistingEnvVars() != null && !ctx.getExistingEnvVars().isEmpty()) {
                finalEnvVars.putAll(ctx.getExistingEnvVars());
                ctx.addLog(String.format("Merged %d existing env vars", ctx.getExistingEnvVars().size()));
            }

            if (ctx.getInputEnvVars() != null && !ctx.getInputEnvVars().isEmpty()) {
                finalEnvVars.putAll(ctx.getInputEnvVars());
                ctx.addLog(String.format("Merged %d input env vars", ctx.getInputEnvVars().size()));
            }
        }

        // Log summary
        ctx.addLog(String.format("Final env vars: %d", finalEnvVars.size()));

        // Log each env var (sorted for readability)
        List<String> keys = new ArrayList<>(finalEnvVars.keySet());
        Collections.sort(keys);

        for (String k : keys) {
            String v = finalEnvVars.get(k);
            String masked = maskValue(k, v);
            ctx.addLog(String.format("  %s=%s", k, masked));
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        ctx.addLog("Nothing to rollback for env stage");
    }

    private Map<String, String> getDefaultEnvVars(DeploymentContext ctx) {
        String slug = ctx.getProjectSlug();

        // Backend config - use external DB credentials from context
        String dbURL = String.format("jdbc:postgresql://%s:%d/%s", 
                ctx.getDbHost(), ctx.getDbPort(), ctx.getDbName());
        String dbName = ctx.getDbName();
        String dbUser = ctx.getDbUsername();
        String dbPassword = ctx.getDbPassword();

        // Frontend URLs
        String backendURL = NamingUtils.fullURL(ctx.getDefaultBackendURL());
        String siteURL = NamingUtils.fullURL(ctx.getDefaultFrontendURL());
        String staticServerURL = NamingUtils.staticURL(ctx.getDefaultFrontendURL());

        return Map.of(
                EnvKeys.ENV_KEY_HOST_BE, "0.0.0.0",
                EnvKeys.ENV_KEY_PORT_BE, "7776",
                EnvKeys.ENV_KEY_DB_URL, dbURL,
                EnvKeys.ENV_KEY_DB_USERNAME, dbUser,
                EnvKeys.ENV_KEY_DB_NAME, dbName,
                EnvKeys.ENV_KEY_DB_PASSWORD, dbPassword,
                EnvKeys.ENV_KEY_VITE_BACKEND_URL, backendURL,
                EnvKeys.ENV_KEY_VITE_SITE_URL, siteURL,
                EnvKeys.ENV_KEY_VITE_STATIC_SERVER_URL, staticServerURL,
                EnvKeys.ENV_KEY_VITE_PORT, "3000");
    }

    private String maskValue(String key, String value) {
        String[] sensitiveKeys = { "PASSWORD", "SECRET", "KEY", "TOKEN", "CREDENTIAL" };
        String upperKey = key.toUpperCase();

        for (String sensitive : sensitiveKeys) {
            if (upperKey.contains(sensitive)) {
                if (value.length() > 4) {
                    return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
                }
                return "****";
            }
        }
        return value;
    }
}
