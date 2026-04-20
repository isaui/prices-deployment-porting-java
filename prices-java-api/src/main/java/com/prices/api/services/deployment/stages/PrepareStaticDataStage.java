package com.prices.api.services.deployment.stages;

import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
public class PrepareStaticDataStage implements PipelineStage {

    @Override
    public String name() {
        return "Prepare Static Data";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        if (ctx.isHasUserCompose()) {
            return;
        }

        Path frontendPath = ctx.getExtractedPath().resolve("frontend");
        if (!Files.exists(frontendPath)) {
            return;
        }

        boolean result = detectAndPrepareStaticData(ctx, frontendPath);
        ctx.setHasStaticData(result);
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        // Static data files are inside extractedPath, cleaned by ExtractStage
    }

    /**
     * Detects json-server database files in the frontend source and prepares
     * individual JSON files for each endpoint so nginx can serve them as
     * static files. This replaces the need for a separate json-server
     * container.
     *
     * json-server DB format: { "endpoint1": {...}, "endpoint2": [...] }
     * Each top-level key becomes /static/{key} served by nginx.
     */
    private boolean detectAndPrepareStaticData(DeploymentContext ctx,
            Path frontendPath) {
        String[] candidates = {
            "src/staticPage/data/static-page-db-production.json",
            "src/staticPage/data/static-page-db.json"
        };

        Path dbFile = null;
        for (String candidate : candidates) {
            Path p = frontendPath.resolve(candidate);
            ctx.addLog(String.format("Checking for static DB: %s -> %s",
                candidate, Files.exists(p) ? "FOUND" : "NOT FOUND"));
            if (Files.exists(p)) {
                dbFile = p;
                break;
            }
        }

        if (dbFile == null) {
            ctx.addLog("Static DB not found at known paths, "
                + "searching recursively...");
            try (Stream<Path> walk = Files.walk(frontendPath, 10)) {
                dbFile = walk
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals(
                                "static-page-db-production.json")
                            || name.equals("static-page-db.json");
                    })
                    .sorted((a, b) -> {
                        boolean aProd = a.getFileName().toString()
                            .contains("production");
                        boolean bProd = b.getFileName().toString()
                            .contains("production");
                        return Boolean.compare(bProd, aProd);
                    })
                    .findFirst()
                    .orElse(null);

                if (dbFile != null) {
                    ctx.addLog("Found static DB via search: "
                        + frontendPath.relativize(dbFile));
                }
            } catch (IOException e) {
                ctx.addLog("Warning: failed to search for static DB: "
                    + e.getMessage());
            }
        }

        if (dbFile == null) {
            ctx.addLog("No json-server DB file found, "
                + "skipping static data preparation");
            return false;
        }

        ctx.addLog("Found json-server DB: " + dbFile.getFileName());

        try {
            String content = Files.readString(dbFile);

            Path staticDataDir = frontendPath.resolve("static-data");
            Files.createDirectories(staticDataDir);

            parseAndWriteStaticEndpoints(content, staticDataDir, ctx);

            String nginxConf = generateNginxConf();
            Files.writeString(frontendPath.resolve("nginx.conf"), nginxConf);
            ctx.addLog("Generated nginx.conf with /static/* routing");

            return true;
        } catch (Exception e) {
            ctx.addLog("Warning: failed to prepare static data: "
                + e.getMessage());
            log.warn("Failed to prepare static data", e);
            return false;
        }
    }

    private void parseAndWriteStaticEndpoints(String json, Path outputDir,
            DeploymentContext ctx) throws IOException {
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IOException("Invalid JSON: not an object");
        }

        String inner = json.substring(1, json.length() - 1).trim();

        int pos = 0;
        while (pos < inner.length()) {
            while (pos < inner.length()
                && Character.isWhitespace(inner.charAt(pos))) pos++;
            if (pos >= inner.length()) break;

            if (inner.charAt(pos) != '"') break;
            int keyStart = pos + 1;
            int keyEnd = inner.indexOf('"', keyStart);
            String key = inner.substring(keyStart, keyEnd);
            pos = keyEnd + 1;

            while (pos < inner.length()
                && (inner.charAt(pos) == ':'
                    || Character.isWhitespace(inner.charAt(pos)))) pos++;

            int valueStart = pos;
            int valueEnd = findValueEnd(inner, pos);
            String value = inner.substring(valueStart, valueEnd).trim();
            pos = valueEnd;

            while (pos < inner.length()
                && (inner.charAt(pos) == ','
                    || Character.isWhitespace(inner.charAt(pos)))) pos++;

            Path keyDir = outputDir.resolve(key);
            Files.createDirectories(keyDir);

            if (value.startsWith("[")) {
                Files.writeString(keyDir.resolve("index.json"), value);
                ctx.addLog(String.format(
                    "  Static endpoint: /static/%s (array)", key));
                writeArrayItemsById(value, keyDir, ctx, key);
            } else {
                Files.writeString(keyDir.resolve("index.json"), value);
                ctx.addLog(String.format(
                    "  Static endpoint: /static/%s (object)", key));
            }
        }
    }

    private void writeArrayItemsById(String arrayJson, Path dir,
            DeploymentContext ctx, String endpointName) throws IOException {
        arrayJson = arrayJson.trim();
        if (!arrayJson.startsWith("[") || !arrayJson.endsWith("]")) return;

        String inner = arrayJson.substring(1, arrayJson.length() - 1).trim();
        int pos = 0;
        int itemCount = 0;

        while (pos < inner.length()) {
            while (pos < inner.length()
                && Character.isWhitespace(inner.charAt(pos))) pos++;
            if (pos >= inner.length()) break;

            if (inner.charAt(pos) == '{') {
                int objEnd = findValueEnd(inner, pos);
                String objStr = inner.substring(pos, objEnd).trim();
                pos = objEnd;

                String id = extractJsonStringField(objStr, "id");
                if (id != null) {
                    Files.writeString(dir.resolve(id + ".json"), objStr);
                    itemCount++;
                }
            }

            while (pos < inner.length()
                && (inner.charAt(pos) == ','
                    || Character.isWhitespace(inner.charAt(pos)))) pos++;
        }

        if (itemCount > 0) {
            ctx.addLog(String.format(
                "  Static endpoint: /static/%s/:id (%d items)",
                endpointName, itemCount));
        }
    }

    private String extractJsonStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        idx += pattern.length();
        while (idx < json.length()
            && (json.charAt(idx) == ':'
                || Character.isWhitespace(json.charAt(idx)))) idx++;

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
                if (c == close) {
                    depth--;
                    if (depth == 0) return i + 1;
                }
            }
        } else if (first == '"') {
            for (int i = start + 1; i < json.length(); i++) {
                if (json.charAt(i) == '\\') { i++; continue; }
                if (json.charAt(i) == '"') return i + 1;
            }
        } else {
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == ',' || c == '}' || c == ']'
                    || Character.isWhitespace(c)) return i;
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

                   location ~ ^/static/([^/]+)/([^/]+)$ {
                       alias /usr/share/nginx/static-data/$1/$2.json;
                       default_type application/json;
                       add_header Access-Control-Allow-Origin *;
                   }

                   location ~ ^/static/([^/]+)/?$ {
                       alias /usr/share/nginx/static-data/$1/index.json;
                       default_type application/json;
                       add_header Access-Control-Allow-Origin *;
                   }

                   location / {
                       try_files $uri $uri/ /index.html;
                   }
               }
               """;
    }
}
