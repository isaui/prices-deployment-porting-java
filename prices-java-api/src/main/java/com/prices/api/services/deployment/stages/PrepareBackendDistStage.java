package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PrepareBackendDistStage implements PipelineStage {

    @Override
    public String name() {
        return "Prepare Backend Distribution";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        if (ctx.isHasUserCompose()) {
            return;
        }

        Path backendPath = ctx.getExtractedPath().resolve("backend");

        if (Files.exists(backendPath)) {
            ctx.addLog("Preparing backend distribution...");
            prepareBackend(ctx, backendPath);
            ctx.setHasBackend(true);
            ctx.addLog("Backend ready: " + backendPath);
        } else {
            ctx.addLog("No backend directory found, skipping");
        }

        if (!ctx.isHasFrontend() && !ctx.isHasBackend()) {
            throw new RuntimeException(
                "no frontend or backend found in artifact");
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Nothing to rollback — files are inside extractedPath
        // which ExtractStage handles
    }

    private void prepareBackend(DeploymentContext ctx, Path backendPath)
            throws IOException {
        BackendRootInfo rootInfo = findBackendRoot(backendPath);
        ctx.addLog(String.format("Backend root found via %s: %s",
            rootInfo.marker, rootInfo.path));

        // Generate .dockerignore if not present
        Path dockerignorePath = rootInfo.path.resolve(".dockerignore");
        if (!Files.exists(dockerignorePath)) {
            generateDockerignore(rootInfo.path);
            ctx.addLog("Generated .dockerignore for backend");
        }

        // Generate Dockerfile if not present
        Path dockerfilePath = rootInfo.path.resolve("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            ctx.addLog(String.format(
                "Generating Dockerfile for backend (%s mode)",
                rootInfo.marker));
            String dockerfile = generateBackendDockerfile();
            Files.writeString(dockerfilePath, dockerfile);
        } else {
            ctx.addLog("Using existing Dockerfile");
        }
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

    private void generateDockerignore(Path rootPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(".git\n");
        sb.append(".gitignore\n");
        sb.append(".gradle\n");
        sb.append("build\n");
        sb.append(".env\n");
        sb.append(".env.*\n");
        sb.append("*.log\n");
        Files.writeString(rootPath.resolve(".dockerignore"), sb.toString());
    }

    private String generateBackendDockerfile() {
        return """
               FROM eclipse-temurin:21-jre-alpine
               WORKDIR /app
               COPY . .
               EXPOSE 7776
               CMD ["sh", "-c", \
               "MODULE_DIR=$(find . -type d -name '*.product.*' | head -1); \
               if [ -z \\"$MODULE_DIR\\" ]; then \
               echo 'ERROR: No product module found!'; exit 1; fi; \
               java -cp $MODULE_DIR --module-path $MODULE_DIR \
               -m $(basename $MODULE_DIR)"]
               """;
    }
}
