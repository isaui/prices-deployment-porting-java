package com.prices.cli.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.prices.cli.api.models.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Client {

    private final String baseUrl;
    private String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Client(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    }

    public String deploy(String projectSlug, Path archivePath, String version, 
                         java.util.function.BiConsumer<Long, Long> progressCallback)
            throws IOException, InterruptedException {
        // Use chunked upload for all files to avoid nginx 413 errors
        return deployChunked(projectSlug, archivePath, version, progressCallback);
    }

    /**
     * Deploy using chunked upload (bypasses nginx size limits)
     */
    public String deployChunked(String projectSlug, Path archivePath, String version,
                                java.util.function.BiConsumer<Long, Long> progressCallback)
            throws IOException, InterruptedException {
        Project project = getProject(projectSlug);
        
        // 1. Upload file in chunks (using projectSlug as upload ID)
        ChunkedUploader uploader = new ChunkedUploader(baseUrl, token);
        uploader.uploadChunked(projectSlug, archivePath, (uploaded, total) -> {
            if (progressCallback != null) {
                progressCallback.accept(uploaded, total);
            }
        });
        
        // 2. Deploy from uploaded file
        Map<String, String> payload = new HashMap<>();
        payload.put("uploadId", projectSlug);  // uploadId = projectSlug
        payload.put("version", version != null ? version : "1.0.0");
        
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/deploy-from-upload"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            try {
                ApiResponse<?> apiResp = objectMapper.readValue(response.body(), ApiResponse.class);
                if (apiResp != null && !apiResp.isSuccess()) {
                    throw new IOException("Deployment failed: " + apiResp.getMessage());
                }
            } catch (IOException e) {
                // ignore parsing error
            }
            throw new IOException("Deployment failed: " + response.statusCode() + " - " + response.body());
        }
        
        ApiResponse<Deployment> apiResponse = objectMapper.readValue(response.body(),
                new TypeReference<ApiResponse<Deployment>>() {});
        if (!apiResponse.isSuccess()) {
            throw new IOException("Deployment failed: " + apiResponse.getMessage());
        }
        
        return String.valueOf(apiResponse.getData().getId());
    }

    public List<Deployment> getDeploymentHistory(String projectSlug) throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/deployments"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<List<Deployment>>>() {
        });
    }

    public Deployment getDeploymentStatus(String deploymentId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/deployments/" + deploymentId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<Deployment>>() {
        });
    }

    public Project createProject(CreateProjectRequest req) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(req);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<Project>>() {
        });
    }

    public List<Project> listProjects() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<List<Project>>>() {
        });
    }

    public Project getProject(String slug) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/slug/" + slug))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<Project>>() {
        });
    }

    public Project updateProject(String slug, UpdateProjectRequest req) throws IOException, InterruptedException {
        // Resolve slug to ID first (since Java client needs to mimic logic or if API
        // supports slug in URL for PUT?)
        // Go client does: GetProject(slug) -> ID -> PUT /api/projects/{id}
        Project project = getProject(slug);

        String body = objectMapper.writeValueAsString(req);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<Project>>() {
        });
    }

    public void deleteProject(String slug) throws IOException, InterruptedException {
        Project project = getProject(slug);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId()))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        sendRequest(request, new TypeReference<ApiResponse<Object>>() {
        });
    }

    public String getProjectLogs(String projectSlug, int lines) throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/logs?lines=" + lines))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        // API returns ApiResponse<LogsResponse> where LogsResponse has { logs, lines }
        Map<String, Object> data = sendRequest(request, new TypeReference<ApiResponse<Map<String, Object>>>() {
        });
        return (String) data.get("logs");
    }

    public void streamDeploymentLogs(String deploymentId, java.util.function.Consumer<String> logConsumer)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/deployments/" + deploymentId + "/stream"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        // Use client with no timeout for SSE streaming (builds can take 10+ minutes)
        HttpClient streamClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // Blocking SSE stream - reads until deployment finishes
        streamClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        logConsumer.accept("Error: " + response.statusCode());
                        return;
                    }
                    response.body().forEach(line -> {
                        if (line.startsWith("data: ")) {
                            logConsumer.accept(line.substring(6));
                        }
                    });
                }).join(); // Block until stream ends (deployment complete)
    }

    public void streamProjectLogs(String projectSlug, java.util.function.Consumer<String> logConsumer)
            throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/logs/stream"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpClient streamClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        streamClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        logConsumer.accept("Error: " + response.statusCode());
                        return;
                    }
                    response.body().forEach(line -> {
                        if (line.startsWith("data: ")) {
                            logConsumer.accept(line.substring(6));
                        }
                    });
                }).join();
    }

    public Map<String, String> getEnvVars(String projectSlug) throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/env-vars"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<ProjectEnvVarsResponse>>() {
        }).getEnvVars();
    }

    public void updateEnvVars(String projectSlug, Map<String, String> envVars)
            throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        Map<String, Object> payload = new HashMap<>();
        payload.put("envVars", envVars);
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/env-vars"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();

        sendRequest(request, new TypeReference<ApiResponse<Object>>() {
        });
    }

    public void replaceEnvVars(String projectSlug, Map<String, String> envVars)
            throws IOException, InterruptedException {
        Project project = getProject(projectSlug);

        Map<String, Object> payload = new HashMap<>();
        payload.put("envVars", envVars);
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/" + project.getId() + "/env-vars"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        sendRequest(request, new TypeReference<ApiResponse<Object>>() {
        });
    }

    public DefaultEnvVarsResponse getDefaultEnvVars() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/projects/env-vars/defaults"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<DefaultEnvVarsResponse>>() {
        });
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getCurrentUser() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return sendRequest(request, new TypeReference<ApiResponse<User>>() {
        });
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private <T> T sendRequest(HttpRequest request, TypeReference<ApiResponse<T>> typeRef)
            throws IOException, InterruptedException {
        return sendRequestWithRetry(request, typeRef, MAX_RETRIES);
    }

    private <T> T sendRequestWithRetry(HttpRequest request, TypeReference<ApiResponse<T>> typeRef, int retriesLeft)
            throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Retry on GOAWAY or connection reset (nginx reload)
            if (retriesLeft > 0 && (e.getMessage().contains("GOAWAY") || e.getMessage().contains("Connection reset"))) {
                Thread.sleep(RETRY_DELAY_MS);
                return sendRequestWithRetry(request, typeRef, retriesLeft - 1);
            }
            throw e;
        }

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            // Try to parse error response
            try {
                ApiResponse<?> apiResp = objectMapper.readValue(response.body(), ApiResponse.class);
                if (apiResp != null && !apiResp.isSuccess()) {
                    throw new IOException("API Error: " + apiResp.getMessage());
                }
            } catch (IOException e) {
                // ignore
            }
            throw new IOException("Request failed: " + response.statusCode() + " - " + response.body());
        }

        if (response.statusCode() >= 500) {
            throw new IOException("Server error: " + response.statusCode());
        }

        ApiResponse<T> apiResponse = objectMapper.readValue(response.body(), typeRef);
        if (!apiResponse.isSuccess()) {
            throw new IOException("API Error: " + apiResponse.getMessage());
        }
        return apiResponse.getData();
    }

    public String login(String identifier, String password) throws IOException, InterruptedException {
        Map<String, String> payload = new HashMap<>();
        payload.put("identifier", identifier);
        payload.put("password", password);

        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // Custom handling for login because response structure might be slightly
        // different in token extraction
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Login failed: " + response.statusCode() + " - " + response.body());
        }

        ApiResponse<Map<String, String>> apiResponse = objectMapper.readValue(response.body(),
                new TypeReference<ApiResponse<Map<String, String>>>() {
                });
        if (!apiResponse.isSuccess()) {
            throw new IOException("Login failed: " + apiResponse.getMessage());
        }

        // Assuming token is inside data map
        // The Go code unmarshals Data into struct { Token string }
        // Here we use Map for flexibility
        Object data = apiResponse.getData();
        // Jackson might deserialize "data" as LinkedHashMap
        if (data instanceof Map) {
            return (String) ((Map<?, ?>) data).get("token");
        }

        throw new IOException("Invalid login response format");
    }

    public String register(String username, String email, String password) throws IOException, InterruptedException {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("password", password);

        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("Registration failed: " + response.statusCode() + " - " + response.body());
        }

        ApiResponse<Map<String, String>> apiResponse = objectMapper.readValue(response.body(),
                new TypeReference<ApiResponse<Map<String, String>>>() {
                });
        if (apiResponse != null && !apiResponse.isSuccess()) {
            throw new IOException("Registration failed: " + apiResponse.getMessage());
        }

        // Go code: var result struct { Token string }
        Object data = apiResponse.getData();
        if (data instanceof Map) {
            return (String) ((Map<?, ?>) data).get("token");
        }

        throw new IOException("Invalid registration response format");
    }
}
