package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import com.prices.api.utils.NamingUtils;
import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            ctx.addLog("User docker-compose.yml found, adding prices-proxy-network...");
            customizeUserCompose(ctx, composePath);
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
        String backendContainer = NamingUtils.containerName("backend", slug);
        String frontendContainer = NamingUtils.containerName("frontend", slug);

        StringBuilder sb = new StringBuilder();
        sb.append("version: '3.8'\n\n");

        sb.append("services:\n");

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
        sb.append("    extra_hosts:\n");
        sb.append("      - \"postgresql:").append(ctx.getDbHost()).append("\"\n");
        sb.append("    labels:\n");
        sb.append("      - \"traefik.enable=true\"\n");
        sb.append("      - \"traefik.http.routers.backend-").append(slug).append(".rule=").append(getBackendHostRule(ctx)).append("\"\n");
        sb.append("      - \"traefik.http.routers.backend-").append(slug).append(".entrypoints=web,websecure\"\n");
        sb.append("      - \"traefik.http.services.backend-").append(slug).append(".loadbalancer.server.port=").append(ctx.getBackendListeningPort()).append("\"\n");
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
        sb.append("    labels:\n");
        sb.append("      - \"traefik.enable=true\"\n");
        sb.append("      - \"traefik.http.routers.frontend-").append(slug).append(".rule=").append(getFrontendHostRule(ctx)).append("\"\n");
        sb.append("      - \"traefik.http.routers.frontend-").append(slug).append(".entrypoints=web,websecure\"\n");
        sb.append("      - \"traefik.http.services.frontend-").append(slug).append(".loadbalancer.server.port=").append(ctx.getFrontendListeningPort()).append("\"\n");
        sb.append("    restart: unless-stopped\n\n");

        // Networks
        sb.append("networks:\n");
        sb.append("  ").append(networkName).append(":\n");
        sb.append("    driver: bridge\n");
        sb.append("  prices-proxy-network:\n");
        sb.append("    external: true\n");

        Files.writeString(composePath, sb.toString());

        writeEnvFile(ctx);

        ctx.addLog("Generated compose file: " + composePath);
    }

    @SuppressWarnings("unchecked")
    private void customizeUserCompose(DeploymentContext ctx, Path composePath) throws IOException {
        String content = Files.readString(composePath);
        Yaml yaml = new Yaml();
        Map<String, Object> compose = yaml.load(content);

        // Get or create services section
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        if (services == null) {
            ctx.addLog("No services found in user compose, skipping customization");
            return;
        }

        String slug = ctx.getProjectSlug();

        // Add prices-proxy-network, extra_hosts, and traefik labels to services
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            Map<String, Object> service = (Map<String, Object>) entry.getValue();
            if (service == null) continue;

            // Add network
            List<String> networks = (List<String>) service.get("networks");
            if (networks == null) {
                networks = new ArrayList<>();
                service.put("networks", networks);
            }
            if (!networks.contains("prices-proxy-network")) {
                networks.add("prices-proxy-network");
            }

            // Add extra_hosts for postgresql
            if (ctx.getDbHost() != null) {
                List<String> extraHosts = (List<String>) service.get("extra_hosts");
                if (extraHosts == null) {
                    extraHosts = new ArrayList<>();
                    service.put("extra_hosts", extraHosts);
                }
                String pgHost = "postgresql:" + ctx.getDbHost();
                if (!extraHosts.contains(pgHost)) {
                    extraHosts.add(pgHost);
                }
            }

            // Add traefik labels for 'backend' or 'frontend' services
            if (serviceName.equals("backend")) {
                List<String> labels = new ArrayList<>();
                labels.add("traefik.enable=true");
                labels.add("traefik.http.routers.backend-" + slug + ".rule=" + getBackendHostRule(ctx));
                labels.add("traefik.http.routers.backend-" + slug + ".entrypoints=web,websecure");
                labels.add("traefik.http.services.backend-" + slug + ".loadbalancer.server.port=" + ctx.getBackendListeningPort());
                service.put("labels", labels);
                ctx.addLog("Added traefik labels to backend service");
            } else if (serviceName.equals("frontend")) {
                List<String> labels = new ArrayList<>();
                labels.add("traefik.enable=true");
                labels.add("traefik.http.routers.frontend-" + slug + ".rule=" + getFrontendHostRule(ctx));
                labels.add("traefik.http.routers.frontend-" + slug + ".entrypoints=web,websecure");
                labels.add("traefik.http.services.frontend-" + slug + ".loadbalancer.server.port=" + ctx.getFrontendListeningPort());
                service.put("labels", labels);
                ctx.addLog("Added traefik labels to frontend service");
            }
        }

        // Get or create networks section
        Map<String, Object> networks = (Map<String, Object>) compose.get("networks");
        if (networks == null) {
            networks = new LinkedHashMap<>();
            compose.put("networks", networks);
        }

        // Add prices-proxy-network as external
        if (!networks.containsKey("prices-proxy-network")) {
            Map<String, Object> proxyNetwork = new LinkedHashMap<>();
            proxyNetwork.put("external", true);
            networks.put("prices-proxy-network", proxyNetwork);
        }

        // Write back
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yamlWriter = new Yaml(options);
        String output = yamlWriter.dump(compose);
        Files.writeString(composePath, output);

        writeEnvFile(ctx);

        ctx.addLog("Customized user compose with prices-proxy-network");
    }

    private String getBackendHostRule(DeploymentContext ctx) {
        List<String> hosts = new ArrayList<>();
        if (ctx.isDefaultBackendActive() && ctx.getDefaultBackendURL() != null) {
            hosts.add("Host(`" + ctx.getDefaultBackendURL() + "`)");
        }
        if (ctx.isCustomBackendActive() && ctx.getCustomBackendURL() != null) {
            hosts.add("Host(`" + ctx.getCustomBackendURL() + "`)");
        }
        // make service not exposed if no host is set
        return hosts.isEmpty() ? "Host(`backend-" + ctx.getProjectSlug() + ".localhost`)" : String.join(" || ", hosts);
    }

    private String getFrontendHostRule(DeploymentContext ctx) {
        List<String> hosts = new ArrayList<>();
        if (ctx.isDefaultFrontendActive() && ctx.getDefaultFrontendURL() != null) {
            hosts.add("Host(`" + ctx.getDefaultFrontendURL() + "`)");
        }
        if (ctx.isCustomFrontendActive() && ctx.getCustomFrontendURL() != null) {
            hosts.add("Host(`" + ctx.getCustomFrontendURL() + "`)");
        }
        // make service not exposed if no host is set
        return hosts.isEmpty() ? "Host(`frontend-" + ctx.getProjectSlug() + ".localhost`)" : String.join(" || ", hosts);
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
