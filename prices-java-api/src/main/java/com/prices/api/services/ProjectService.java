package com.prices.api.services;

import com.prices.api.dto.requests.CreateProjectRequest;
import com.prices.api.dto.requests.UpdateProjectRequest;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.Project;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

public interface ProjectService {
    Project create(Long userId, CreateProjectRequest req);

    Project getById(Long id);

    Project getBySlug(String slug);

    List<Project> getByUserId(Long userId);

    List<Project> getAll();

    Project update(Long id, UpdateProjectRequest req);

    void delete(Long id);

    List<DeploymentHistory> getDeploymentHistory(Long projectId);

    String getLogs(Long projectId, int lines);

    Flux<String> streamLogs(Long projectId);

    Map<String, String> getEnvVars(Long projectId);

    void updateEnvVars(Long projectId, Map<String, String> envVars);

    void upsertEnvVars(Long projectId, Map<String, String> envVars);

    Project createInternal(Project project);
}
