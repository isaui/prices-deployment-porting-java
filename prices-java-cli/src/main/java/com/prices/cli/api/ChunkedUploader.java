package com.prices.cli.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.cli.api.models.ApiResponse;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Handles chunked file uploads to bypass nginx size limits
 */
public class ChunkedUploader {

    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5MB per chunk
    
    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChunkedUploader(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Upload a file in chunks with progress callback
     * Returns the projectSlug (used as upload ID)
     */
    public String uploadChunked(String projectSlug, Path filePath, ProgressCallback progressCallback) 
            throws IOException, InterruptedException {
        
        long fileSize = Files.size(filePath);
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        String fileName = filePath.getFileName().toString();
        
        // 1. Initialize upload session with projectSlug
        initUpload(projectSlug, fileName, fileSize, totalChunks);
        
        // 2. Upload chunks
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(filePath))) {
            byte[] buffer = new byte[CHUNK_SIZE];
            long totalUploaded = 0;
            
            for (int i = 0; i < totalChunks; i++) {
                int bytesRead = fis.read(buffer);
                if (bytesRead <= 0) break;
                
                byte[] chunkData = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                uploadChunk(projectSlug, i, chunkData);
                
                totalUploaded += bytesRead;
                if (progressCallback != null) {
                    progressCallback.onProgress(totalUploaded, fileSize);
                }
            }
        }
        
        // 3. Finalize upload
        finalizeUpload(projectSlug);
        
        return projectSlug;
    }

    private void initUpload(String projectSlug, String fileName, long totalSize, int totalChunks) 
            throws IOException, InterruptedException {
        
        String body = objectMapper.writeValueAsString(Map.of(
            "projectSlug", projectSlug,
            "fileName", fileName,
            "totalSize", totalSize,
            "totalChunks", totalChunks
        ));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/uploads/init"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to init upload: " + response.statusCode() + " - " + response.body());
        }
        
        ApiResponse<Map<String, Object>> apiResponse = objectMapper.readValue(response.body(),
                new TypeReference<ApiResponse<Map<String, Object>>>() {});
        
        if (!apiResponse.isSuccess()) {
            throw new IOException("Failed to init upload: " + apiResponse.getMessage());
        }
    }

    private void uploadChunk(String projectSlug, int index, byte[] chunkData) 
            throws IOException, InterruptedException {
        
        String boundary = "---ChunkBoundary" + System.currentTimeMillis();
        
        HttpRequest.BodyPublisher bodyPublisher = MultipartBodyPublisher.newBuilder()
                .boundary(boundary)
                .addBinaryPart("chunk", "chunk.bin", chunkData, "application/octet-stream")
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/uploads/" + projectSlug + "/chunk?index=" + index))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(bodyPublisher)
                .timeout(Duration.ofMinutes(2))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to upload chunk " + index + ": " + response.statusCode() + " - " + response.body());
        }
    }

    private void finalizeUpload(String projectSlug) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/uploads/" + projectSlug + "/finalize"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofMinutes(2))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to finalize upload: " + response.statusCode() + " - " + response.body());
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long uploaded, long total);
    }
}
