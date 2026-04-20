package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PrepareFrontendDistStage implements PipelineStage {

    @Override
    public String name() {
        return "Prepare Frontend Distribution";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        if (ctx.isHasUserCompose()) {
            return;
        }

        Path frontendPath = ctx.getExtractedPath().resolve("frontend");

        if (!Files.exists(frontendPath)) {
            ctx.addLog("No frontend directory found, skipping");
            return;
        }

        ctx.addLog("Preparing frontend distribution...");

        FrontendRootInfo rootInfo = findFrontendRoot(frontendPath);
        ctx.addLog(String.format("Frontend root found via %s: %s",
            rootInfo.marker, rootInfo.path));

        // Generate .dockerignore if not present
        Path dockerignorePath = rootInfo.path.resolve(".dockerignore");
        if (!Files.exists(dockerignorePath)) {
            generateDockerignore(rootInfo.path, rootInfo.marker);
            ctx.addLog("Generated .dockerignore for frontend");
        }

        // Extract frontend env vars
        Map<String, String> frontendEnvVars =
            filterFrontendEnvVars(ctx.getFinalEnvVars());
        ctx.setFrontendBuildArgs(frontendEnvVars);

        // Generate Dockerfile if not present
        Path dockerfilePath = rootInfo.path.resolve("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            ctx.addLog(String.format(
                "Generating Dockerfile for frontend (%s mode)",
                rootInfo.marker));
            String dockerfile = generateFrontendDockerfile(
                rootInfo.marker, frontendEnvVars, ctx.isHasStaticData());
            Files.writeString(dockerfilePath, dockerfile);
            if (!frontendEnvVars.isEmpty()) {
                ctx.addLog(String.format(
                    "Dockerfile includes %d build args (VITE_*/REACT_*)",
                    frontendEnvVars.size()));
            }
            if (ctx.isHasStaticData()) {
                ctx.addLog("Dockerfile includes static data server "
                    + "(json-server replacement via nginx)");
            }
        } else {
            ctx.addLog("Using existing Dockerfile");
        }

        ctx.setHasFrontend(true);
        ctx.addLog("Frontend ready: " + rootInfo.path);
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Files are inside extractedPath, cleaned by ExtractStage
    }

    private static class FrontendRootInfo {
        Path path;
        String marker;

        FrontendRootInfo(Path path, String marker) {
            this.path = path;
            this.marker = marker;
        }
    }

    private FrontendRootInfo findFrontendRoot(Path basePath)
            throws IOException {
        if (Files.exists(basePath.resolve("Dockerfile")))
            return new FrontendRootInfo(basePath, "dockerfile");

        if (Files.exists(basePath.resolve("package.json")))
            return new FrontendRootInfo(basePath, "package.json");

        Path buildPath = basePath.resolve("build");
        if (Files.exists(buildPath) && Files.isDirectory(buildPath))
            return new FrontendRootInfo(buildPath, "static");

        Path distPath = basePath.resolve("dist");
        if (Files.exists(distPath) && Files.isDirectory(distPath))
            return new FrontendRootInfo(distPath, "static");

        throw new IOException("could not find frontend root");
    }

    private void generateDockerignore(Path rootPath, String marker)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("node_modules\n");
        sb.append(".git\n");
        sb.append(".gitignore\n");
        sb.append(".env\n");
        sb.append(".env.*\n");
        if ("package.json".equals(marker)) {
            sb.append("dist\n");
            sb.append("build\n");
        }
        Files.writeString(rootPath.resolve(".dockerignore"), sb.toString());
    }

    private Map<String, String> filterFrontendEnvVars(
            Map<String, String> envVars) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (entry.getKey().startsWith("VITE_")
                || entry.getKey().startsWith("REACT_APP_")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String generateFrontendDockerfile(String marker,
            Map<String, String> envVars, boolean hasStaticData) {
        if ("package.json".equals(marker)) {
            StringBuilder sb = new StringBuilder();
            sb.append("FROM node:20-alpine AS builder\n");
            sb.append("WORKDIR /app\n");

            for (String k : envVars.keySet()) {
                sb.append("ARG ").append(k).append("\n");
            }
            for (String k : envVars.keySet()) {
                sb.append("ENV ").append(k).append("=$")
                  .append(k).append("\n");
            }

            sb.append("COPY package*.json ./\n");
            sb.append("RUN npm ci\n");
            sb.append("COPY . .\n");
            sb.append("RUN npm run build\n");
            sb.append("\n");
            sb.append("# Move output to consistent location\n");
            sb.append("RUN if [ -d \"dist\" ]; then mv dist output; ");
            sb.append("elif [ -d \"build\" ]; then mv build output; fi\n");
            sb.append("\n");
            sb.append("FROM nginx:alpine\n");
            sb.append("COPY --from=builder /app/output ");
            sb.append("/usr/share/nginx/html\n");

            if (hasStaticData) {
                sb.append("COPY static-data ");
                sb.append("/usr/share/nginx/static-data\n");
                sb.append("COPY nginx.conf ");
                sb.append("/etc/nginx/conf.d/default.conf\n");
            }

            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("FROM nginx:alpine\n");
            sb.append("COPY . /usr/share/nginx/html\n");

            if (hasStaticData) {
                sb.append("COPY static-data ");
                sb.append("/usr/share/nginx/static-data\n");
                sb.append("COPY nginx.conf ");
                sb.append("/etc/nginx/conf.d/default.conf\n");
            }

            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            return sb.toString();
        }
    }
}
