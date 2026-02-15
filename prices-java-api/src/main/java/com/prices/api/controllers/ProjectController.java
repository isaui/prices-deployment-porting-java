package com.prices.api.controllers;

import com.prices.api.dto.requests.CreateProjectRequest;
import com.prices.api.dto.requests.UpdateEnvVarsRequest;
import com.prices.api.dto.requests.UpdateProjectRequest;
import com.prices.api.handlers.ProjectHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.security.Principal;

@Controller("/api/projects")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProjectController {

    private final ProjectHandler handler;

    @Post
    public HttpResponse<?> create(Principal principal, @Body CreateProjectRequest req) {
        Long userId = Long.parseLong(principal.getName());
        return handler.create(userId, req);
    }

    @Get
    public HttpResponse<?> getAll() {
        return handler.getAll();
    }

    @Get("/{id}")
    public HttpResponse<?> getById(@PathVariable Long id) {
        return handler.getById(id);
    }

    @Get("/slug/{slug}")
    public HttpResponse<?> getBySlug(@PathVariable String slug) {
        return handler.getBySlug(slug);
    }

    @Put("/{id}")
    public HttpResponse<?> update(@PathVariable Long id, @Body UpdateProjectRequest req) {
        return handler.update(id, req);
    }

    @Delete("/{id}")
    public HttpResponse<?> delete(@PathVariable Long id) {
        return handler.delete(id);
    }
    
    @Get("/env-vars/defaults")
    public HttpResponse<?> getDefaultEnvVars() {
        return handler.getDefaultEnvVars();
    }

    @Get("/{id}/env-vars")
    public HttpResponse<?> getEnvVars(@PathVariable Long id) {
        return handler.getEnvVars(id);
    }

    @Put("/{id}/env-vars")
    public HttpResponse<?> replaceEnvVars(@PathVariable Long id, @Body UpdateEnvVarsRequest req) {
        return handler.replaceEnvVars(id, req);
    }

    @Patch("/{id}/env-vars")
    public HttpResponse<?> upsertEnvVars(@PathVariable Long id, @Body UpdateEnvVarsRequest req) {
        return handler.upsertEnvVars(id, req);
    }

    @Get("/{id}/logs")
    public HttpResponse<?> getLogs(@PathVariable Long id, @QueryValue(defaultValue = "50") int lines) {
        return handler.getLogs(id, lines);
    }

    @Get(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public Flux<String> streamLogs(@PathVariable Long id) {
        return handler.streamLogs(id);
    }
}
