package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
public class PrepareDistStage implements PipelineStage {

    @Override
    public String name() {
        return "Prepare Distribution";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        // Skip if user provides docker-compose.yml
        Path composePath = ctx.getExtractedPath().resolve("docker-compose.yml");
        if (Files.exists(composePath)) {
            ctx.addLog("User docker-compose.yml found, skipping dist preparation");
            ctx.setHasUserCompose(true);
            return;
        }

        Path frontendPath = ctx.getExtractedPath().resolve("frontend");
        Path backendPath = ctx.getExtractedPath().resolve("backend");

        // Prepare frontend
        if (Files.exists(frontendPath)) {
            ctx.addLog("Preparing frontend distribution...");
            Path distPath = prepareFrontend(ctx, frontendPath);
            ctx.setFrontendDistPath(distPath);
            ctx.addLog("Frontend dist ready: " + distPath);
        } else {
            ctx.addLog("No frontend directory found, skipping");
        }

        // Prepare backend
        if (Files.exists(backendPath)) {
            ctx.addLog("Preparing backend distribution...");
            Path distPath = prepareBackend(ctx, backendPath);
            ctx.setBackendDistPath(distPath);
            ctx.addLog("Backend dist ready: " + distPath);
        } else {
            ctx.addLog("No backend directory found, skipping");
        }

        if (ctx.getFrontendDistPath() == null && ctx.getBackendDistPath() == null) {
            throw new RuntimeException("no frontend or backend found in artifact");
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        if (ctx.getFrontendDistPath() != null) {
            deleteDirectory(ctx.getFrontendDistPath());
        }
        if (ctx.getBackendDistPath() != null) {
            deleteDirectory(ctx.getBackendDistPath());
        }
        ctx.addLog("Cleaned up dist directories");
    }

    private Path prepareFrontend(DeploymentContext ctx, Path srcPath) throws IOException {
        Path distPath = ctx.getExtractedPath().resolve("frontend-dist");

        FrontendRootInfo rootInfo = findFrontendRoot(srcPath);
        ctx.addLog(String.format("Frontend root found via %s: %s", rootInfo.marker, rootInfo.path));

        copyDir(rootInfo.path, distPath);

        // Clean build output folders for package.json mode
        if ("package.json".equals(rootInfo.marker)) {
            String[] folders = { "dist", "build", "node_modules" };
            for (String folder : folders) {
                Path folderPath = distPath.resolve(folder);
                if (Files.exists(folderPath)) {
                    ctx.addLog(String.format("Removing %s/ (will be rebuilt)", folder));
                    deleteDirectory(folderPath);
                }
            }
        }

        // Extract frontend env vars
        Map<String, String> frontendEnvVars = filterFrontendEnvVars(ctx.getFinalEnvVars());
        ctx.setFrontendBuildArgs(frontendEnvVars);

        // Generate Dockerfile if not present
        Path dockerfilePath = distPath.resolve("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            ctx.addLog(String.format("Generating Dockerfile for frontend (%s mode)", rootInfo.marker));
            String dockerfile = generateFrontendDockerfile(rootInfo.marker, frontendEnvVars);
            Files.writeString(dockerfilePath, dockerfile);
            if (!frontendEnvVars.isEmpty()) {
                ctx.addLog(String.format("Dockerfile includes %d build args (VITE_*/REACT_*)", frontendEnvVars.size()));
            }
        } else {
            ctx.addLog("Using existing Dockerfile");
        }

        return distPath;
    }

    private Path prepareBackend(DeploymentContext ctx, Path srcPath) throws IOException {
        Path distPath = ctx.getExtractedPath().resolve("backend-dist");

        BackendRootInfo rootInfo = findBackendRoot(srcPath);
        ctx.addLog(String.format("Backend root found via %s: %s", rootInfo.marker, rootInfo.path));

        copyDir(rootInfo.path, distPath);

        Path dockerfilePath = distPath.resolve("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            ctx.addLog(String.format("Generating Dockerfile for backend (%s mode)", rootInfo.marker));
            String dockerfile = generateBackendDockerfile();
            Files.writeString(dockerfilePath, dockerfile);
        } else {
            ctx.addLog("Using existing Dockerfile");
        }

        return distPath;
    }

    private static class FrontendRootInfo {
        Path path;
        String marker;

        FrontendRootInfo(Path path, String marker) {
            this.path = path;
            this.marker = marker;
        }
    }

    private FrontendRootInfo findFrontendRoot(Path basePath) throws IOException {
        // Priority 1: Dockerfile
        if (Files.exists(basePath.resolve("Dockerfile")))
            return new FrontendRootInfo(basePath, "dockerfile");

        // Priority 2: package.json
        if (Files.exists(basePath.resolve("package.json")))
            return new FrontendRootInfo(basePath, "package.json");

        // Priority 3: build/dist folder
        Path buildPath = basePath.resolve("build");
        if (Files.exists(buildPath) && Files.isDirectory(buildPath))
            return new FrontendRootInfo(buildPath, "static");

        Path distPath = basePath.resolve("dist");
        if (Files.exists(distPath) && Files.isDirectory(distPath))
            return new FrontendRootInfo(distPath, "static");

        throw new IOException("could not find frontend root");
    }

    private static class BackendRootInfo {
        Path path;
        String marker;

        BackendRootInfo(Path path, String marker) {
            this.path = path;
            this.marker = marker;
        }
    }

    private BackendRootInfo findBackendRoot(Path basePath) {
        if (Files.exists(basePath.resolve("Dockerfile"))) {
            return new BackendRootInfo(basePath, "dockerfile");
        }
        return new BackendRootInfo(basePath, "product-module");
    }

    private Map<String, String> filterFrontendEnvVars(Map<String, String> envVars) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (entry.getKey().startsWith("VITE_") || entry.getKey().startsWith("REACT_APP_")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String generateFrontendDockerfile(String marker, Map<String, String> envVars) {
        if ("package.json".equals(marker)) {
            StringBuilder sb = new StringBuilder();
            sb.append("FROM node:20-alpine AS builder\n");
            sb.append("WORKDIR /app\n");

            for (String k : envVars.keySet()) {
                sb.append("ARG ").append(k).append("\n");
            }
            for (String k : envVars.keySet()) {
                sb.append("ENV ").append(k).append("=$").append(k).append("\n");
            }

            sb.append("COPY package*.json ./\n");
            sb.append("RUN npm ci\n");
            sb.append("COPY . .\n");
            sb.append("RUN npm run build\n");
            sb.append("\n");
            sb.append("# Move output to consistent location\n");
            sb.append("RUN if [ -d \"dist\" ]; then mv dist output; elif [ -d \"build\" ]; then mv build output; fi\n");
            sb.append("\n");
            sb.append("FROM nginx:alpine\n");
            sb.append("COPY --from=builder /app/output /usr/share/nginx/html\n");
            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            return sb.toString();
        } else {
            return "FROM nginx:alpine\n" +
                    "COPY . /usr/share/nginx/html\n" +
                    "EXPOSE 80\n" +
                    "CMD [\"sh\", \"-c\", \"if [ -z \\\"$(ls -A /usr/share/nginx/html)\\\" ]; then echo 'ERROR: No static files found!'; exit 1; fi; nginx -g 'daemon off;'\"]\n";
        }
    }

    private String generateBackendDockerfile() {
        return "FROM eclipse-temurin:21-jre-alpine\n" +
                "WORKDIR /app\n" +
                "COPY . .\n" +
                "EXPOSE 7776\n" +
                "CMD [\"sh\", \"-c\", \"MODULE_DIR=$(find . -type d -name '*.product.*' | head -1); if [ -z \\\"$MODULE_DIR\\\" ]; then echo 'ERROR: No product module found!'; exit 1; fi; java -cp $MODULE_DIR --module-path $MODULE_DIR -m $(basename $MODULE_DIR)\"]\n";
    }

    private void copyDir(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> {
                Path destination = dest.resolve(src.relativize(source));
                try {
                    Files.copy(source, destination);
                } catch (IOException e) {
                    // Ignore if directory exists
                }
            });
        }
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path))
            return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", path, e);
        }
    }
}
