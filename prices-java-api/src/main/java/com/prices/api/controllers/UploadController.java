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
    public HttpResponse<?> initUpload(@Body InitUploadRequest request) {
        return handler.initUpload(request.getProjectSlug(), request.getFileName(), request.getTotalSize(), request.getTotalChunks());
    }

    @Post(value = "/{projectSlug}/chunk", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> uploadChunk(
            @PathVariable String projectSlug,
            @QueryValue int index,
            @Part("chunk") CompletedFileUpload chunk) {
        return handler.uploadChunk(projectSlug, index, chunk);
    }

    @Post("/{projectSlug}/finalize")
    public HttpResponse<?> finalizeUpload(@PathVariable String projectSlug) {
        return handler.finalizeUpload(projectSlug);
    }

    @Get("/{projectSlug}/status")
    public HttpResponse<?> getStatus(@PathVariable String projectSlug) {
        return handler.getStatus(projectSlug);
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
