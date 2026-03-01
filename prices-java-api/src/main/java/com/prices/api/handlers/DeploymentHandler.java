package com.prices.api.handlers;

import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.services.DeploymentService;
import com.prices.api.utils.MapperUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.multipart.CompletedFileUpload;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import com.prices.api.models.Project;
import com.prices.api.services.ProjectService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

@Singleton
@RequiredArgsConstructor
public class DeploymentHandler {

    private final DeploymentService deploymentService;
    private final ProjectService projectService;

    public Publisher<Event<String>> getLogsStream(Long deploymentId, Long userId, String role) {
        try {
            DeploymentHistory deployment = deploymentService.getStatus(deploymentId);
            Project project = projectService.getById(deployment.getProjectId());
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return null; // Will result in empty stream
            }
            return deploymentService.getLogEvents(deploymentId);
        } catch (Exception e) {
            return null;
        }
    }

    public HttpResponse<?> deploy(Long projectId, Long userId, String role, CompletedFileUpload artifact, String version) {
        if (artifact == null) {
            return HttpResponse.badRequest(ErrorResponse.error("Artifact file is required"));
        }

        try {
            Project project = projectService.getById(projectId);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
            
            byte[] artifactData = artifact.getBytes();
            DeployRequest req = new DeployRequest();
            req.setProjectID(projectId);
            req.setUserID(userId);
            req.setVersion(version != null ? version : "1.0.0");
            req.setArtifactData(artifactData);

            DeploymentHistory deployment = deploymentService.deploy(req);
            return HttpResponse.status(HttpStatus.ACCEPTED).body(
                    ApiResponse.success("Deployment started", MapperUtils.toDeploymentHistoryResponse(deployment)));
        } catch (IOException e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to read artifact file"));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error(e.getMessage()));
        }
    }

    public HttpResponse<?> getHistory(Long projectId, Long userId, String role) {
        try {
            Project project = projectService.getById(projectId);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
            
            List<DeploymentHistory> history = deploymentService.getHistory(projectId);
            return HttpResponse.ok(ApiResponse.success("Deployment history retrieved", MapperUtils.toDeploymentHistoryListResponse(history)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to get deployment history"));
        }
    }

    public HttpResponse<?> getStatus(Long deploymentId, Long userId, String role) {
        try {
            DeploymentHistory deployment = deploymentService.getStatus(deploymentId);
            Project project = projectService.getById(deployment.getProjectId());
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
            
            return HttpResponse.ok(ApiResponse.success("Deployment status retrieved",
                    MapperUtils.toDeploymentHistoryResponse(deployment)));
        } catch (Exception e) {
            return HttpResponse.notFound(ErrorResponse.error("Deployment not found"));
        }
    }

    public HttpResponse<?> deployFromUpload(Long projectId, Long userId, String role, Path artifactPath, String version) {
        if (artifactPath == null || !Files.exists(artifactPath)) {
            return HttpResponse.badRequest(ErrorResponse.error("Artifact file not found"));
        }

        try {
            Project project = projectService.getById(projectId);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }
            
            byte[] artifactData = Files.readAllBytes(artifactPath);
            DeployRequest req = new DeployRequest();
            req.setProjectID(projectId);
            req.setUserID(userId);
            req.setVersion(version != null ? version : "1.0.0");
            req.setArtifactData(artifactData);

            DeploymentHistory deployment = deploymentService.deploy(req);
            return HttpResponse.status(HttpStatus.ACCEPTED).body(
                    ApiResponse.success("Deployment started", MapperUtils.toDeploymentHistoryResponse(deployment)));
        } catch (IOException e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to read artifact file"));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error(e.getMessage()));
        }
    }
}
