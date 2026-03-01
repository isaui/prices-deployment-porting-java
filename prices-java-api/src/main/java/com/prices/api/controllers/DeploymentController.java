package com.prices.api.controllers;

import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.handlers.DeploymentHandler;
import com.prices.api.handlers.UploadHandler;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.security.Principal;

import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

@Controller
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class DeploymentController {

    private final DeploymentHandler handler;
    private final UploadHandler uploadHandler;

    @Get(value = "/api/deployments/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> getLogs(@PathVariable Long id) {
        return handler.getLogsStream(id);
    }

    @Post(value = "/api/projects/{id}/deploy", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> deploy(
            Principal principal,
            @PathVariable Long id,
            @Part("artifact") CompletedFileUpload artifact,
            @Part("version") @Nullable String version) {
        Long userId = Long.parseLong(principal.getName());
        return handler.deploy(id, userId, artifact, version);
    }

    @Get("/api/projects/{id}/deployments")
    public HttpResponse<?> getHistory(@PathVariable Long id) {
        return handler.getHistory(id);
    }

    @Get("/api/deployments/{id}")
    public HttpResponse<?> getStatus(@PathVariable Long id) {
        return handler.getStatus(id);
    }

    /**
     * Deploy from a previously uploaded chunked file
     */
    @Post("/api/projects/{id}/deploy-from-upload")
    public HttpResponse<?> deployFromUpload(
            Principal principal,
            @PathVariable Long id,
            @Body DeployFromUploadRequest request) {
        Long userId = Long.parseLong(principal.getName());
        
        Path artifactPath = uploadHandler.getFinalPath(request.getUploadId());
        if (artifactPath == null) {
            return HttpResponse.badRequest(ApiResponse.error("Upload not found or not finalized"));
        }
        
        HttpResponse<?> response = handler.deployFromUpload(id, userId, artifactPath, request.getVersion());
        
        // Cleanup upload after deployment starts
        uploadHandler.cleanupSession(request.getUploadId());
        
        return response;
    }

    @Data
    @Introspected
    @Serdeable
    public static class DeployFromUploadRequest {
        private String uploadId;
        private String version;
    }
}
