package com.prices.api.controllers;

import com.prices.api.handlers.UploadHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Controller("/api/uploads")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class UploadController {

    private final UploadHandler handler;

    @Post("/init")
    public HttpResponse<?> initUpload(Authentication auth, @Body InitUploadRequest request) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.initUpload(request.getProjectSlug(), request.getFileName(), request.getTotalSize(), request.getTotalChunks(), userId, role);
    }

    @Post(value = "/{projectSlug}/chunk", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> uploadChunk(
            Authentication auth,
            @PathVariable String projectSlug,
            @QueryValue int index,
            @Part("chunk") CompletedFileUpload chunk) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.uploadChunk(projectSlug, index, chunk, userId, role);
    }

    @Post("/{projectSlug}/finalize")
    public HttpResponse<?> finalizeUpload(Authentication auth, @PathVariable String projectSlug) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.finalizeUpload(projectSlug, userId, role);
    }

    @Get("/{projectSlug}/status")
    public HttpResponse<?> getStatus(Authentication auth, @PathVariable String projectSlug) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getStatus(projectSlug, userId, role);
    }

    @Data
    @Introspected
    @Serdeable
    public static class InitUploadRequest {
        private String projectSlug;
        private String fileName;
        private long totalSize;
        private int totalChunks;
    }
}
