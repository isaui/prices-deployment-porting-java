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

        // Detect json-server static data (used for /static/* endpoints)
        boolean hasStaticData = detectAndPrepareStaticData(ctx, distPath);

        // Generate Dockerfile if not present
        Path dockerfilePath = distPath.resolve("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            ctx.addLog(String.format("Generating Dockerfile for frontend (%s mode)", rootInfo.marker));
            String dockerfile = generateFrontendDockerfile(rootInfo.marker, frontendEnvVars, hasStaticData);
            Files.writeString(dockerfilePath, dockerfile);
            if (!frontendEnvVars.isEmpty()) {
                ctx.addLog(String.format("Dockerfile includes %d build args (VITE_*/REACT_*)", frontendEnvVars.size()));
            }
            if (hasStaticData) {
                ctx.addLog("Dockerfile includes static data server (json-server replacement via nginx)");
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

    private String generateFrontendDockerfile(String marker, Map<String, String> envVars, boolean hasStaticData) {
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

            if (hasStaticData) {
                sb.append("COPY static-data /usr/share/nginx/static-data\n");
                sb.append("COPY nginx.conf /etc/nginx/conf.d/default.conf\n");
            }

            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("FROM nginx:alpine\n");
            sb.append("COPY . /usr/share/nginx/html\n");

            if (hasStaticData) {
                sb.append("COPY static-data /usr/share/nginx/static-data\n");
                sb.append("COPY nginx.conf /etc/nginx/conf.d/default.conf\n");
            }

            sb.append("EXPOSE 80\n");
            sb.append("CMD [\"sh\", \"-c\", \"if [ -z \\\\\"$(ls -A /usr/share/nginx/html)\\\\\" ]; then echo 'ERROR: No static files found!'; exit 1; fi; nginx -g 'daemon off;'\"]\n");
            return sb.toString();
        }
    }

    private String generateBackendDockerfile() {
        return """
               FROM eclipse-temurin:21-jre-alpine
               WORKDIR /app
               COPY . .
               EXPOSE 7776
               CMD ["sh", "-c", "MODULE_DIR=$(find . -type d -name '*.product.*' | head -1); if [ -z \\"$MODULE_DIR\\" ]; then echo 'ERROR: No product module found!'; exit 1; fi; java -cp $MODULE_DIR --module-path $MODULE_DIR -m $(basename $MODULE_DIR)"]
               """;
    }

    /**
     * Detects json-server database files in the frontend source and prepares
     * individual JSON files for each endpoint so nginx can serve them as static
     * files. This replaces the need for a separate json-server container.
     *
     * json-server DB format: { "endpoint1": {...}, "endpoint2": [...] }
     * Each top-level key becomes /static/{key} served by nginx.
     */
    private boolean detectAndPrepareStaticData(DeploymentContext ctx, Path distPath) {
        // Look for json-server DB files (production first, then dev)
        String[] candidates = {
            "src/staticPage/data/static-page-db-production.json",
            "src/staticPage/data/static-page-db.json"
        };

        Path dbFile = null;
        for (String candidate : candidates) {
            Path p = distPath.resolve(candidate);
            ctx.addLog(String.format("Checking for static DB: %s -> %s", candidate, Files.exists(p) ? "FOUND" : "NOT FOUND"));
            if (Files.exists(p)) {
                dbFile = p;
                break;
            }
        }

        // Fallback: search recursively for the file
        if (dbFile == null) {
            ctx.addLog("Static DB not found at known paths, searching recursively...");
            try (Stream<Path> walk = Files.walk(distPath, 10)) {
                dbFile = walk
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("static-page-db-production.json") || name.equals("static-page-db.json");
                    })
                    .sorted((a, b) -> {
                        // Prefer production over dev
                        boolean aProd = a.getFileName().toString().contains("production");
                        boolean bProd = b.getFileName().toString().contains("production");
                        return Boolean.compare(bProd, aProd);
                    })
                    .findFirst()
                    .orElse(null);
                
                if (dbFile != null) {
                    ctx.addLog("Found static DB via search: " + distPath.relativize(dbFile));
                }
            } catch (IOException e) {
                ctx.addLog("Warning: failed to search for static DB: " + e.getMessage());
            }
        }

        if (dbFile == null) {
            ctx.addLog("No json-server DB file found, skipping static data preparation");
            return false;
        }

        ctx.addLog("Found json-server DB: " + dbFile.getFileName());

        try {
            String content = Files.readString(dbFile);

            // Create static-data directory in dist root
            Path staticDataDir = distPath.resolve("static-data");
            Files.createDirectories(staticDataDir);

            // Parse top-level keys from JSON and write each as separate file
            // Simple parsing: find top-level "key": value pairs
            // We use a basic approach since we don't want to add a JSON library dependency
            // The json-server DB is a simple object with top-level keys
            parseAndWriteStaticEndpoints(content, staticDataDir, ctx);

            // Generate nginx config
            String nginxConf = generateNginxConf();
            Files.writeString(distPath.resolve("nginx.conf"), nginxConf);
            ctx.addLog("Generated nginx.conf with /static/* routing");

            return true;
        } catch (Exception e) {
            ctx.addLog("Warning: failed to prepare static data: " + e.getMessage());
            log.warn("Failed to prepare static data", e);
            return false;
        }
    }

    /**
     * Parses a json-server DB file and writes each top-level key as a separate JSON file.
     * For object values (like "appearance"), writes as-is.
     * For array values (like "static-data"), writes the full array and individual items by id.
     */
    private void parseAndWriteStaticEndpoints(String json, Path outputDir, DeploymentContext ctx) throws IOException {
        // Minimal JSON parsing for json-server DB format
        // We need to split top-level keys into separate files
        // Format: { "key1": {...}, "key2": [...], ... }

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IOException("Invalid JSON: not an object");
        }

        // Remove outer braces
        String inner = json.substring(1, json.length() - 1).trim();

        int pos = 0;
        while (pos < inner.length()) {
            // Skip whitespace
            while (pos < inner.length() && Character.isWhitespace(inner.charAt(pos))) pos++;
            if (pos >= inner.length()) break;

            // Parse key
            if (inner.charAt(pos) != '"') break;
            int keyStart = pos + 1;
            int keyEnd = inner.indexOf('"', keyStart);
            String key = inner.substring(keyStart, keyEnd);
            pos = keyEnd + 1;

            // Skip colon and whitespace
            while (pos < inner.length() && (inner.charAt(pos) == ':' || Character.isWhitespace(inner.charAt(pos)))) pos++;

            // Parse value (find matching bracket/brace)
            int valueStart = pos;
            int valueEnd = findValueEnd(inner, pos);
            String value = inner.substring(valueStart, valueEnd).trim();
            pos = valueEnd;

            // Skip comma
            while (pos < inner.length() && (inner.charAt(pos) == ',' || Character.isWhitespace(inner.charAt(pos)))) pos++;

            // Write the endpoint file
            Path keyDir = outputDir.resolve(key);
            Files.createDirectories(keyDir);

            if (value.startsWith("[")) {
                // Array: write full array as index.json
                Files.writeString(keyDir.resolve("index.json"), value);
                ctx.addLog(String.format("  Static endpoint: /static/%s (array)", key));

                // Also write individual items by "id" field
                writeArrayItemsById(value, keyDir, ctx, key);
            } else {
                // Object or primitive: write as index.json
                Files.writeString(keyDir.resolve("index.json"), value);
                ctx.addLog(String.format("  Static endpoint: /static/%s (object)", key));
            }
        }
    }

    private void writeArrayItemsById(String arrayJson, Path dir, DeploymentContext ctx, String endpointName) throws IOException {
        // Find items with "id" fields and write them as individual files
        // Look for patterns like {"id":"someId", ...}
        arrayJson = arrayJson.trim();
        if (!arrayJson.startsWith("[") || !arrayJson.endsWith("]")) return;

        String inner = arrayJson.substring(1, arrayJson.length() - 1).trim();
        int pos = 0;
        int itemCount = 0;

        while (pos < inner.length()) {
            while (pos < inner.length() && Character.isWhitespace(inner.charAt(pos))) pos++;
            if (pos >= inner.length()) break;

            if (inner.charAt(pos) == '{') {
                int objEnd = findValueEnd(inner, pos);
                String objStr = inner.substring(pos, objEnd).trim();
                pos = objEnd;

                // Extract id from object
                String id = extractJsonStringField(objStr, "id");
                if (id != null) {
                    Files.writeString(dir.resolve(id + ".json"), objStr);
                    itemCount++;
                }
            }

            // Skip comma
            while (pos < inner.length() && (inner.charAt(pos) == ',' || Character.isWhitespace(inner.charAt(pos)))) pos++;
        }

        if (itemCount > 0) {
            ctx.addLog(String.format("  Static endpoint: /static/%s/:id (%d items)", endpointName, itemCount));
        }
    }

    private String extractJsonStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        idx += pattern.length();
        // Skip whitespace and colon
        while (idx < json.length() && (json.charAt(idx) == ':' || Character.isWhitespace(json.charAt(idx)))) idx++;

        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        int start = idx + 1;
        int end = json.indexOf('"', start);
        if (end < 0) return null;

        return json.substring(start, end);
    }

    private int findValueEnd(String json, int start) {
        char first = json.charAt(start);
        if (first == '{' || first == '[') {
            char open = first;
            char close = (first == '{') ? '}' : ']';
            int depth = 1;
            boolean inString = false;
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && inString) { i++; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (inString) continue;
                if (c == open) depth++;
                if (c == close) { depth--; if (depth == 0) return i + 1; }
            }
        } else if (first == '"') {
            for (int i = start + 1; i < json.length(); i++) {
                if (json.charAt(i) == '\\') { i++; continue; }
                if (json.charAt(i) == '"') return i + 1;
            }
        } else {
            // number, boolean, null
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) return i;
            }
        }
        return json.length();
    }

    private String generateNginxConf() {
        return """
               server {
                   listen 80;
                   server_name _;
                   root /usr/share/nginx/html;
                   index index.html;

                   # Serve /static/{resource}/{id} from individual JSON files
                   location ~ ^/static/([^/]+)/([^/]+)$ {
                       alias /usr/share/nginx/static-data/$1/$2.json;
                       default_type application/json;
                       add_header Access-Control-Allow-Origin *;
                   }

                   # Serve /static/{resource} from index.json
                   location ~ ^/static/([^/]+)/?$ {
                       alias /usr/share/nginx/static-data/$1/index.json;
                       default_type application/json;
                       add_header Access-Control-Allow-Origin *;
                   }

                   # SPA fallback
                   location / {
                       try_files $uri $uri/ /index.html;
                   }
               }
               """;
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
