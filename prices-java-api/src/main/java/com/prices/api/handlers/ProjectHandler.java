package com.prices.api.handlers;

import com.prices.api.constants.EnvKeys;
import com.prices.api.dto.requests.CreateProjectRequest;
import com.prices.api.dto.requests.UpdateEnvVarsRequest;
import com.prices.api.dto.requests.UpdateProjectRequest;
import com.prices.api.dto.responses.*;
import com.prices.api.models.Project;
import com.prices.api.services.ProjectService;
import com.prices.api.utils.MapperUtils;
import com.prices.api.utils.NamingUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Singleton
@RequiredArgsConstructor
public class ProjectHandler {

    private final ProjectService projectService;

    public HttpResponse<?> create(Long userId, CreateProjectRequest req) {
        if (req.getName() == null || req.getName().isEmpty()) {
            return HttpResponse.badRequest(ErrorResponse.error("Name is required"));
        }

        try {
            Project project = projectService.create(userId, req);
            return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success("Project created successfully", MapperUtils.toProjectResponse(project)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to create project"));
        }
    }

    public HttpResponse<?> getAll() {
        try {
            List<Project> projects = projectService.getAll();
            return HttpResponse.ok(ApiResponse.success("Projects retrieved successfully", MapperUtils.toProjectListResponse(projects)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to get projects"));
        }
    }

    public HttpResponse<?> getById(Long id) {
        try {
            Project project = projectService.getById(id);
            return HttpResponse.ok(ApiResponse.success("Project retrieved successfully", MapperUtils.toProjectResponse(project)));
        } catch (Exception e) {
            return HttpResponse.notFound(ErrorResponse.error("Project not found"));
        }
    }

    public HttpResponse<?> getBySlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return HttpResponse.badRequest(ErrorResponse.error("Slug is required"));
        }
        try {
            Project project = projectService.getBySlug(slug);
            return HttpResponse.ok(ApiResponse.success("Project retrieved successfully", MapperUtils.toProjectResponse(project)));
        } catch (Exception e) {
            return HttpResponse.notFound(ErrorResponse.error("Project not found"));
        }
    }

    public HttpResponse<?> update(Long id, UpdateProjectRequest req) {
        try {
            Project project = projectService.update(id, req);
            return HttpResponse.ok(ApiResponse.success("Project updated successfully", MapperUtils.toProjectResponse(project)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to update project"));
        }
    }

    public HttpResponse<?> delete(Long id) {
        try {
            projectService.delete(id);
            return HttpResponse.ok(ApiResponse.success("Project deleted successfully", null));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to delete project"));
        }
    }

    public HttpResponse<?> getEnvVars(Long id) {
        try {
            Map<String, String> envVars = projectService.getEnvVars(id);
            return HttpResponse.ok(ApiResponse.success("Project env vars retrieved", new ProjectEnvVarsResponse(id, envVars)));
        } catch (Exception e) {
            return HttpResponse.notFound(ErrorResponse.error("Project not found"));
        }
    }

    public HttpResponse<?> replaceEnvVars(Long id, UpdateEnvVarsRequest req) {
        if (req.getEnvVars() == null) {
            return HttpResponse.badRequest(ErrorResponse.error("env_vars is required"));
        }
        try {
            projectService.updateEnvVars(id, req.getEnvVars());
            Map<String, String> envVars = projectService.getEnvVars(id);
            return HttpResponse.ok(ApiResponse.success("Project env vars replaced", new ProjectEnvVarsResponse(id, envVars)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to replace env vars"));
        }
    }

    public HttpResponse<?> upsertEnvVars(Long id, UpdateEnvVarsRequest req) {
        if (req.getEnvVars() == null) {
            return HttpResponse.badRequest(ErrorResponse.error("env_vars is required"));
        }
        try {
            projectService.upsertEnvVars(id, req.getEnvVars());
            Map<String, String> envVars = projectService.getEnvVars(id);
            return HttpResponse.ok(ApiResponse.success("Project env vars updated", new ProjectEnvVarsResponse(id, envVars)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to upsert env vars"));
        }
    }

    public HttpResponse<?> getDefaultEnvVars() {
        DefaultEnvVarsResponse data = new DefaultEnvVarsResponse();
        
        data.setBackend(List.of(
            new EnvVarDefinition(EnvKeys.ENV_KEY_HOST_BE, "Backend host address", "0.0.0.0"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_PORT_BE, "Backend port", "7776"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_DB_URL, "PostgreSQL connection URL", "postgres://postgres:xxx@postgres:5432/myproject?sslmode=disable"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_DB_USERNAME, "Database username", "postgres"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_DB_NAME, "Database name (uses project slug)", "myproject"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_DB_PASSWORD, "Database password (auto-generated)", "randomly_generated_16_chars")
        ));
        
        data.setFrontend(List.of(
            new EnvVarDefinition(EnvKeys.ENV_KEY_VITE_BACKEND_URL, "Backend API URL for frontend", "https://backend-myproject.prices.fasilkom.app"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_VITE_SITE_URL, "Frontend site URL", "https://frontend-myproject.prices.fasilkom.app"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_VITE_STATIC_SERVER_URL, "Static server URL", "https://frontend-myproject.prices.fasilkom.app/static"),
            new EnvVarDefinition(EnvKeys.ENV_KEY_VITE_PORT, "Frontend dev server port", "3000")
        ));
        
        return HttpResponse.ok(ApiResponse.success("Auto-provisioned environment variables", data));
    }
    
    public HttpResponse<?> getLogs(Long id, int lines) {
        try {
            String logs = projectService.getLogs(id, lines);
            return HttpResponse.ok(new LogsResponse(logs, lines));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to get logs: " + e.getMessage()));
        }
    }
    
    public Flux<String> streamLogs(Long id) {
        return projectService.streamLogs(id);
    }
}
