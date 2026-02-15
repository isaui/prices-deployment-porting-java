package com.prices.api.controllers;

import com.prices.api.handlers.DeploymentHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;

import java.security.Principal;

import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

@Controller
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class DeploymentController {

    private final DeploymentHandler handler;

    @Get(value = "/api/deployments/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> getLogs(@PathVariable Long id) {
        return handler.getLogsStream(id);
    }

    @Post(value = "/api/projects/{id}/deploy", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpResponse<?> deploy(
            Principal principal,
            @PathVariable Long id,
            @Part("artifact") CompletedFileUpload artifact,
            @Part(value = "version", defaultValue = "1.0.0") String version,
            @Part(value = "env_vars") @Nullable String envVars) {
        Long userId = Long.parseLong(principal.getName());
        return handler.deploy(id, userId, artifact, version, envVars);
    }

    @Get("/api/projects/{id}/deployments")
    public HttpResponse<?> getHistory(@PathVariable Long id) {
        return handler.getHistory(id);
    }

    @Get("/api/deployments/{id}")
    public HttpResponse<?> getStatus(@PathVariable Long id) {
        return handler.getStatus(id);
    }
}
