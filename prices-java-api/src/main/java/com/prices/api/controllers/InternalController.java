package com.prices.api.controllers;

import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.handlers.DeploymentHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal API endpoints protected by X-Internal-Api-Key header.
 * Used by IDE for deploy/ssh operations without user authentication.
 */
@Slf4j
@Controller("/api/internal")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_ANONYMOUS)  // Auth handled by InternalApiKeyFilter
public class InternalController {

    private final DeploymentHandler deploymentHandler;

    @Get("/health")
    public HttpResponse<?> health() {
        return HttpResponse.ok(ApiResponse.success("Internal API is healthy", null));
    }

    @Get("/projects/{projectSlug}/logs")
    public HttpResponse<?> getProjectLogs(
            @PathVariable String projectSlug,
            @QueryValue(defaultValue = "100") int lines) {
        log.info("Internal API: Getting logs for project: {}", projectSlug);
        // TODO: Implement get logs by slug
        return HttpResponse.ok(ApiResponse.success("Logs retrieved", null));
    }

    @Post("/projects/{projectSlug}/ssh")
    public HttpResponse<?> sshToProject(
            @PathVariable String projectSlug,
            @Body SshRequest request) {
        log.info("Internal API: SSH command for project: {}", projectSlug);
        // TODO: Implement SSH command execution
        return HttpResponse.ok(ApiResponse.success("SSH command executed", null));
    }

    public static class SshRequest {
        public String container;  // frontend, backend
        public String command;
    }
}
