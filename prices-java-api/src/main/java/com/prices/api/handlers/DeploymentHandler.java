package com.prices.api.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.DeploymentListResponse;
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
import java.util.Map;

import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

@Singleton
@RequiredArgsConstructor
public class DeploymentHandler {

    private final DeploymentService deploymentService;
    private final ObjectMapper objectMapper;

    public Publisher<Event<String>> getLogsStream(Long deploymentId) {
        return deploymentService.getLogEvents(deploymentId);
    }

    public HttpResponse<?> deploy(Long projectId, Long userId, CompletedFileUpload artifact, String version,
            String envVarsJson) {
        if (artifact == null) {
            return HttpResponse.badRequest(ErrorResponse.error("Artifact file is required"));
        }

        Map<String, String> inputEnvVars = null;
        if (envVarsJson != null && !envVarsJson.isEmpty()) {
            try {
                inputEnvVars = objectMapper.readValue(envVarsJson, new TypeReference<Map<String, String>>() {
                });
            } catch (JsonProcessingException e) {
                return HttpResponse.badRequest(ErrorResponse.error("Invalid env_vars JSON format"));
            }
        }

        try {
            byte[] artifactData = artifact.getBytes();
            DeployRequest req = new DeployRequest();
            req.setProjectID(projectId);
            req.setUserID(userId);
            req.setVersion(version != null ? version : "1.0.0");
            req.setArtifactData(artifactData);
            req.setInputEnvVars(inputEnvVars);

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
            return HttpResponse.ok(ApiResponse.success("Deployment history retrieved",
                    new DeploymentListResponse(MapperUtils.toDeploymentHistoryListResponse(history))));
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
