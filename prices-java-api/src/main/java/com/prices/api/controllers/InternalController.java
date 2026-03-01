package com.prices.api.controllers;

import com.prices.api.dto.requests.CreateInternalProjectRequest;
import com.prices.api.dto.requests.DeployInternalProjectRequest;
import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.dto.requests.InitUploadRequest;
import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.handlers.DeploymentHandler;
import com.prices.api.handlers.UploadHandler;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.Project;
import com.prices.api.services.DeploymentService;
import com.prices.api.services.ProjectService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Internal API endpoints protected by X-Internal-Api-Key header.
 * Used by IDE for deploy/ssh operations without user authentication.
 */
@Slf4j
@Controller("/api/internal")
@RequiredArgsConstructor
@Secured(SecurityRule.IS_ANONYMOUS)  // Auth handled by InternalApiKeyFilter
public class InternalController {

    private final DeploymentHandler deploymentHandler;
    private final UploadHandler uploadHandler;
    private final ProjectService projectService;
    private final DeploymentService deploymentService;

    @Get("/health")
    public HttpResponse<?> health() {
        return HttpResponse.ok(ApiResponse.success("Internal API is healthy", null));
    }

    // =========================================================================
    // Create Project
    // =========================================================================

    @Post("/projects")
    public HttpResponse<?> createProject(@Body CreateInternalProjectRequest request) {
        log.info("Internal API: Creating SSH project: {}", request.getName());
        
        try {
            Project project = new Project();
            project.setName(request.getName());
            project.setSlug(request.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-"));
            project.setProjectType("ssh");
            project.setUserId(null);  // SSH projects have no user
            
            if (request.getFrontendUrl() != null) {
                project.setCustomFrontendUrl(request.getFrontendUrl());
                project.setCustomFrontendActive(true);
            }
            if (request.getBackendUrl() != null) {
                project.setCustomBackendUrl(request.getBackendUrl());
                project.setCustomBackendActive(true);
            }
            if (request.getFrontendPort() != null) {
                project.setFrontendListeningPort(request.getFrontendPort());
            }
            if (request.getBackendPort() != null) {
                project.setBackendListeningPort(request.getBackendPort());
            }
            
            Project saved = projectService.createInternal(project);
            
            return HttpResponse.created(ApiResponse.success("Project created", Map.of(
                    "projectId", saved.getId(),
                    "slug", saved.getSlug()
            )));
        } catch (Exception e) {
            log.error("Failed to create project", e);
            return HttpResponse.serverError(ApiResponse.error("Failed to create project: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Upload Endpoints
    // =========================================================================

    @Post("/uploads/init")
    public HttpResponse<?> initUpload(@Body InitUploadRequest request) {
        log.info("Internal API: Init upload for project: {}", request.getProjectSlug());
        return uploadHandler.initUploadInternal(
                request.getProjectSlug(),
                request.getFileName(),
                request.getTotalSize(),
                request.getTotalChunks()
        );
    }

    @Post(value = "/uploads/{projectSlug}/chunk", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> uploadChunk(
            @PathVariable String projectSlug,
            @QueryValue int index,
            @Part("chunk") CompletedFileUpload chunk) {
        return uploadHandler.uploadChunkInternal(projectSlug, index, chunk);
    }

    @Post("/uploads/{projectSlug}/finalize")
    public HttpResponse<?> finalizeUpload(@PathVariable String projectSlug) {
        return uploadHandler.finalizeUploadInternal(projectSlug);
    }

    // =========================================================================
    // Deploy Endpoint
    // =========================================================================

    @Post("/projects/{projectId}/deploy")
    public HttpResponse<?> deploy(@PathVariable Long projectId, @Body DeployInternalProjectRequest request) {
        log.info("Internal API: Deploy project ID: {}", projectId);
        
        try {
            Project project = projectService.getById(projectId);
            if (project == null) {
                return HttpResponse.notFound(ApiResponse.error("Project not found"));
            }
            
            // Get artifact path from upload
            Path artifactPath = uploadHandler.getFinalPath(project.getSlug());
            if (artifactPath == null || !Files.exists(artifactPath)) {
                return HttpResponse.badRequest(ApiResponse.error("Artifact not uploaded or not finalized"));
            }
            
            // Set env vars if provided
            if (request.getEnvVars() != null && !request.getEnvVars().isEmpty()) {
                projectService.upsertEnvVars(projectId, request.getEnvVars());
            }
            
            // Build deploy request
            DeployRequest deployReq = new DeployRequest();
            deployReq.setProjectID(projectId);
            deployReq.setUserID(project.getUserId());
            deployReq.setVersion(request.getVersion() != null ? request.getVersion() : "1.0.0");
            deployReq.setArtifactData(Files.readAllBytes(artifactPath));
            
            // Start deployment
            DeploymentHistory deployment = deploymentService.deploy(deployReq);
            
            // Cleanup upload
            uploadHandler.cleanupSession(project.getSlug());
            
            return HttpResponse.accepted().body(ApiResponse.success("Deployment started", Map.of(
                    "deploymentId", deployment.getId(),
                    "projectId", project.getId(),
                    "status", deployment.getStatus()
            )));
            
        } catch (Exception e) {
            log.error("Deploy failed", e);
            return HttpResponse.serverError(ApiResponse.error("Deploy failed: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Deployment Stream
    // =========================================================================

    @Get(value = "/deployments/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> streamDeploymentLogs(@PathVariable Long id) {
        log.info("Internal API: Streaming logs for deployment: {}", id);
        return deploymentService.getLogEvents(id);
    }

}
