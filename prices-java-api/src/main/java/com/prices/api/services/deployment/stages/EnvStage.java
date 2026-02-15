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

        // 1. Start with defaults (auto-provided)
        Map<String, String> defaults = getDefaultEnvVars(ctx);
        finalEnvVars.putAll(defaults);
        ctx.addLog(String.format("Applied %d default env vars", defaults.size()));

        // 2. Merge ExistingEnvVars (overrides defaults)
        if (ctx.getExistingEnvVars() != null && !ctx.getExistingEnvVars().isEmpty()) {
            finalEnvVars.putAll(ctx.getExistingEnvVars());
            ctx.addLog(String.format("Merged %d existing env vars", ctx.getExistingEnvVars().size()));
        }

        // 3. Merge InputEnvVars (overrides both)
        if (ctx.getInputEnvVars() != null && !ctx.getInputEnvVars().isEmpty()) {
            finalEnvVars.putAll(ctx.getInputEnvVars());
            ctx.addLog(String.format("Merged %d input env vars", ctx.getInputEnvVars().size()));
        }

        // 4. Ensure all default keys are present (fill missing ones)
        int missingCount = 0;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!finalEnvVars.containsKey(entry.getKey())) {
                finalEnvVars.put(entry.getKey(), entry.getValue());
                missingCount++;
            }
        }
        if (missingCount > 0) {
            ctx.addLog(String.format("Restored %d missing default env vars", missingCount));
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

        // Backend config
        String dbURL = NamingUtils.databaseURL(slug);
        String dbName = slug;
        String dbUser = "postgres";
        String dbPassword = NamingUtils.generateSecurePassword(16);

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
