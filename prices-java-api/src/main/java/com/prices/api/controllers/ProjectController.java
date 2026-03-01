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

import io.micronaut.security.authentication.Authentication;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

@Controller("/api/projects")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProjectController {

    private final ProjectHandler handler;

    @Post
    public HttpResponse<?> create(Authentication auth, @Body CreateProjectRequest req) {
        Long userId = Long.parseLong(auth.getName());
        return handler.create(userId, req);
    }

    @Get
    public HttpResponse<?> getAll(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getAll(userId, role);
    }

    @Get("/me")
    public HttpResponse<?> getProjectByMe(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return handler.getByUserId(userId);
    }

    @Get("/{id}")
    public HttpResponse<?> getById(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getById(id, userId, role);
    }

    @Get("/slug/{slug}")
    public HttpResponse<?> getBySlug(Authentication auth, @PathVariable String slug) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getBySlug(slug, userId, role);
    }

    @Put("/{id}")
    public HttpResponse<?> update(Authentication auth, @PathVariable Long id, @Body UpdateProjectRequest req) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.update(id, req, userId, role);
    }

    @Delete("/{id}")
    public HttpResponse<?> delete(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.delete(id, userId, role);
    }
    
    @Get("/env-vars/defaults")
    public HttpResponse<?> getDefaultEnvVars() {
        return handler.getDefaultEnvVars();
    }

    @Get("/{id}/env-vars")
    public HttpResponse<?> getEnvVars(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getEnvVars(id, userId, role);
    }

    @Put("/{id}/env-vars")
    public HttpResponse<?> replaceEnvVars(Authentication auth, @PathVariable Long id, @Body UpdateEnvVarsRequest req) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.replaceEnvVars(id, req, userId, role);
    }

    @Patch("/{id}/env-vars")
    public HttpResponse<?> upsertEnvVars(Authentication auth, @PathVariable Long id, @Body UpdateEnvVarsRequest req) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.upsertEnvVars(id, req, userId, role);
    }

    @Get("/{id}/logs")
    public HttpResponse<?> getLogs(Authentication auth, @PathVariable Long id, @QueryValue(defaultValue = "50") int lines) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getLogs(id, lines, userId, role);
    }

    @Get(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public Flux<String> streamLogs(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.streamLogs(id, userId, role);
    }
}
