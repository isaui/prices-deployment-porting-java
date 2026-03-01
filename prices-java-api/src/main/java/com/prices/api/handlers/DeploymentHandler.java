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

import java.io.IOException;
import java.util.List;

import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

@Singleton
@RequiredArgsConstructor
public class DeploymentHandler {

    private final DeploymentService deploymentService;

    public Publisher<Event<String>> getLogsStream(Long deploymentId) {
        return deploymentService.getLogEvents(deploymentId);
    }

    public HttpResponse<?> deploy(Long projectId, Long userId, CompletedFileUpload artifact, String version) {
        if (artifact == null) {
            return HttpResponse.badRequest(ErrorResponse.error("Artifact file is required"));
        }

        try {
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

    public HttpResponse<?> getHistory(Long projectId) {
        try {
            List<DeploymentHistory> history = deploymentService.getHistory(projectId);
            return HttpResponse.ok(ApiResponse.success("Deployment history retrieved", MapperUtils.toDeploymentHistoryListResponse(history)));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to get deployment history"));
        }
    }

    public HttpResponse<?> getStatus(Long deploymentId) {
        try {
            DeploymentHistory deployment = deploymentService.getStatus(deploymentId);
            return HttpResponse.ok(ApiResponse.success("Deployment status retrieved",
                    MapperUtils.toDeploymentHistoryResponse(deployment)));
        } catch (Exception e) {
            return HttpResponse.notFound(ErrorResponse.error("Deployment not found"));
        }
    }
}
