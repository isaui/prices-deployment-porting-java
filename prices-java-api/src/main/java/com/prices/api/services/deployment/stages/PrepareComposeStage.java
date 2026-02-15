package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class PrepareComposeStage implements PipelineStage {

    @Override
    public String name() {
        return "PrepareCompose";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        Path composePath = ctx.getExtractedPath().resolve("docker-compose.yml");

        if (ctx.isHasUserCompose()) {
            ctx.addLog("User docker-compose.yml found, customizing volumes/networks...");
            // TODO: Implement user compose customization (yaml parsing)
            // For now we assume generated compose for this MVP port or simple passthrough
            // customizeUserCompose(ctx, composePath);
        } else {
            ctx.addLog("Generating docker-compose.yml...");
            generateNew(ctx, composePath);
        }

        ctx.setComposePath(composePath);
        ctx.setNetworkName(NamingUtils.networkName(ctx.getProjectSlug()));
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Nothing to rollback
    }

    private void generateNew(DeploymentContext ctx, Path composePath) throws IOException {
        String slug = ctx.getProjectSlug();
        String networkName = NamingUtils.networkName(slug);
        String postgresContainer = NamingUtils.containerName("postgres", slug);
        String backendContainer = NamingUtils.containerName("backend", slug);
        String frontendContainer = NamingUtils.containerName("frontend", slug);
        String postgresVolume = NamingUtils.volumeName("postgres_data", slug);

        StringBuilder sb = new StringBuilder();
        sb.append("version: '3.8'\n\n");

        sb.append("services:\n");

        // Postgres
        sb.append("  postgres:\n");
        sb.append("    image: postgres:15-alpine\n");
        sb.append("    container_name: ").append(postgresContainer).append("\n");
        sb.append("    environment:\n");
        sb.append("      POSTGRES_DB: ").append(slug).append("\n");
        sb.append("      POSTGRES_USER: ${AMANAH_DB_USERNAME}\n");
        sb.append("      POSTGRES_PASSWORD: ${AMANAH_DB_PASSWORD}\n");
        sb.append("    volumes:\n");
        sb.append("      - ").append(postgresVolume).append(":/var/lib/postgresql/data\n");
        sb.append("    networks:\n");
        sb.append("      - ").append(networkName).append("\n");
        sb.append("    healthcheck:\n");
        sb.append("      test: [\"CMD-SHELL\", \"pg_isready -U ${AMANAH_DB_USERNAME}\"]\n");
        sb.append("      interval: 10s\n");
        sb.append("      timeout: 5s\n");
        sb.append("      retries: 5\n");
        sb.append("    restart: unless-stopped\n\n");

        // Backend
        sb.append("  backend:\n");
        sb.append("    build:\n");
        sb.append("      context: ./backend-dist\n");
        sb.append("      dockerfile: Dockerfile\n");
        sb.append("    image: ").append(backendContainer).append(":latest\n");
        sb.append("    container_name: ").append(backendContainer).append("\n");
        sb.append("    env_file:\n");
        sb.append("      - .env\n");
        sb.append("    networks:\n");
        sb.append("      - ").append(networkName).append("\n");
        sb.append("      - prices-proxy-network\n");
        sb.append("    depends_on:\n");
        sb.append("      postgres:\n");
        sb.append("        condition: service_healthy\n");
        sb.append("    restart: unless-stopped\n\n");

        // Frontend
        sb.append("  frontend:\n");
        sb.append("    build:\n");
        sb.append("      context: ./frontend-dist\n");
        sb.append("      dockerfile: Dockerfile\n");

        // Frontend args
        if (!ctx.getFrontendBuildArgs().isEmpty()) {
            sb.append("      args:\n");
            for (String k : ctx.getFrontendBuildArgs().keySet()) {
                sb.append("        ").append(k).append(": ${").append(k).append("}\n");
            }
        }

        sb.append("    image: ").append(frontendContainer).append(":latest\n");
        sb.append("    container_name: ").append(frontendContainer).append("\n");
        sb.append("    networks:\n");
        sb.append("      - ").append(networkName).append("\n");
        sb.append("      - prices-proxy-network\n");
        sb.append("    restart: unless-stopped\n\n");

        // Networks
        sb.append("networks:\n");
        sb.append("  ").append(networkName).append(":\n");
        sb.append("    driver: bridge\n");
        sb.append("  prices-proxy-network:\n");
        sb.append("    external: true\n\n");

        // Volumes
        sb.append("volumes:\n");
        sb.append("  ").append(postgresVolume).append(":\n");

        Files.writeString(composePath, sb.toString());

        writeEnvFile(ctx);

        ctx.addLog("Generated compose file: " + composePath);
    }

    private void writeEnvFile(DeploymentContext ctx) throws IOException {
        if (ctx.getFinalEnvVars().isEmpty())
            return;

        Path envPath = ctx.getExtractedPath().resolve(".env");
        StringBuilder envContent = new StringBuilder();
        for (Map.Entry<String, String> entry : ctx.getFinalEnvVars().entrySet()) {
            envContent.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        Files.writeString(envPath, envContent.toString());
        ctx.addLog(String.format("Generated .env file with %d variables", ctx.getFinalEnvVars().size()));
    }
}
