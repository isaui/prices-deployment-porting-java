package com.prices.api.handlers;

import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.services.UploadService;
import com.prices.api.services.UploadService.UploadSession;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.multipart.CompletedFileUpload;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.models.Project;
import com.prices.api.services.ProjectService;
import io.micronaut.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class UploadHandler {

    private final UploadService uploadService;
    private final ProjectService projectService;

    public HttpResponse<?> initUpload(String projectSlug, String fileName, long totalSize, int totalChunks, Long userId, String role) {
        try {
            Project project = projectService.getBySlug(projectSlug);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
        } catch (Exception e) {
            return HttpResponse.notFound(ApiResponse.error("Project not found"));
        }
        
        UploadSession session = uploadService.initUpload(projectSlug, fileName, totalSize, totalChunks);
        if (session == null) {
            return HttpResponse.serverError(ApiResponse.error("Failed to init upload"));
        }
        
        return HttpResponse.ok(ApiResponse.success(Map.of(
            "uploadId", session.projectSlug(),
            "chunkSize", 5 * 1024 * 1024 // 5MB recommended chunk size
        )));
    }

    public HttpResponse<?> uploadChunk(String projectSlug, int index, CompletedFileUpload chunk, Long userId, String role) {
        try {
            Project project = projectService.getBySlug(projectSlug);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
        } catch (Exception e) {
            return HttpResponse.notFound(ApiResponse.error("Project not found"));
        }
        
        try {
            byte[] chunkData = chunk.getBytes();
            int uploaded = uploadService.uploadChunk(projectSlug, index, chunkData);
            
            UploadSession session = uploadService.getStatus(projectSlug);
            int total = session != null ? session.totalChunks() : 0;
            
            return HttpResponse.ok(ApiResponse.success(Map.of(
                "uploaded", uploaded,
                "total", total,
                "complete", uploaded == total
            )));
        } catch (UploadService.UploadException e) {
            if (e.getMessage().contains("not found")) {
                return HttpResponse.notFound(ApiResponse.error(e.getMessage()));
            }
            return HttpResponse.badRequest(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return HttpResponse.serverError(ApiResponse.error("Failed to read chunk: " + e.getMessage()));
        }
    }

    public HttpResponse<?> finalizeUpload(String projectSlug, Long userId, String role) {
        try {
            Project project = projectService.getBySlug(projectSlug);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
        } catch (Exception e) {
            return HttpResponse.notFound(ApiResponse.error("Project not found"));
        }
        
        try {
            Path finalPath = uploadService.finalizeUpload(projectSlug);
            UploadSession session = uploadService.getStatus(projectSlug);
            
            return HttpResponse.ok(ApiResponse.success(Map.of(
                "uploadId", projectSlug,
                "path", finalPath.toString(),
                "size", session != null ? session.totalSize() : 0
            )));
        } catch (UploadService.UploadException e) {
            if (e.getMessage().contains("not found")) {
                return HttpResponse.notFound(ApiResponse.error(e.getMessage()));
            }
            return HttpResponse.badRequest(ApiResponse.error(e.getMessage()));
        }
    }

    public HttpResponse<?> getStatus(String projectSlug, Long userId, String role) {
        try {
            Project project = projectService.getBySlug(projectSlug);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
        } catch (Exception e) {
            return HttpResponse.notFound(ApiResponse.error("Project not found"));
        }
        
        UploadSession session = uploadService.getStatus(projectSlug);
        if (session == null) {
            return HttpResponse.notFound(ApiResponse.error("Upload session not found"));
        }
        
        return HttpResponse.ok(ApiResponse.success(Map.of(
            "uploadId", session.projectSlug(),
            "uploadedChunks", session.uploadedChunks(),
            "totalChunks", session.totalChunks(),
            "complete", session.uploadedChunks() == session.totalChunks(),
            "finalized", session.finalized()
        )));
    }

    public Path getFinalPath(String projectSlug) {
        return uploadService.getFinalPath(projectSlug);
    }

    public void cleanupSession(String projectSlug) {
        uploadService.cleanupSession(projectSlug);
    }

    // =========================================================================
    // Internal methods (no auth checks - used by InternalController)
    // =========================================================================

    public HttpResponse<?> initUploadInternal(String projectSlug, String fileName, long totalSize, int totalChunks) {
        UploadSession session = uploadService.initUpload(projectSlug, fileName, totalSize, totalChunks);
        if (session == null) {
            return HttpResponse.serverError(ApiResponse.error("Failed to init upload"));
        }
        
        return HttpResponse.ok(ApiResponse.success(Map.of(
            "uploadId", session.projectSlug(),
            "chunkSize", 5 * 1024 * 1024
        )));
    }

    public HttpResponse<?> uploadChunkInternal(String projectSlug, int index, CompletedFileUpload chunk) {
        try {
            byte[] chunkData = chunk.getBytes();
            int uploaded = uploadService.uploadChunk(projectSlug, index, chunkData);
            
            UploadSession session = uploadService.getStatus(projectSlug);
            int total = session != null ? session.totalChunks() : 0;
            
            return HttpResponse.ok(ApiResponse.success(Map.of(
                "uploaded", uploaded,
                "total", total,
                "complete", uploaded == total
            )));
        } catch (UploadService.UploadException e) {
            if (e.getMessage().contains("not found")) {
                return HttpResponse.notFound(ApiResponse.error(e.getMessage()));
            }
            return HttpResponse.badRequest(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return HttpResponse.serverError(ApiResponse.error("Failed to read chunk: " + e.getMessage()));
        }
    }

    public HttpResponse<?> finalizeUploadInternal(String projectSlug) {
        try {
            Path finalPath = uploadService.finalizeUpload(projectSlug);
            UploadSession session = uploadService.getStatus(projectSlug);
            
            return HttpResponse.ok(ApiResponse.success(Map.of(
                "uploadId", projectSlug,
                "path", finalPath.toString(),
                "size", session != null ? session.totalSize() : 0
            )));
        } catch (UploadService.UploadException e) {
            if (e.getMessage().contains("not found")) {
                return HttpResponse.notFound(ApiResponse.error(e.getMessage()));
            }
            return HttpResponse.badRequest(ApiResponse.error(e.getMessage()));
        }
    }
}
