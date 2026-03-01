package com.prices.api.utils;

import com.prices.api.dto.responses.DeploymentHistoryResponse;
import com.prices.api.dto.responses.ProjectResponse;
import com.prices.api.dto.responses.UserResponse;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.Project;
import com.prices.api.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MapperUtils {

    public static ProjectResponse toProjectResponse(Project p) {
        if (p == null)
            return null;

        ProjectResponse resp = new ProjectResponse();
        resp.setId(p.getId());
        resp.setName(p.getName());
        resp.setSlug(p.getSlug());
        resp.setDescription(p.getDescription());
        resp.setStatus(p.getStatus());

        resp.setDefaultFrontendUrl(p.getDefaultFrontendUrl());
        resp.setCustomFrontendUrl(p.getCustomFrontendUrl());
        resp.setDefaultFrontendActive(p.isDefaultFrontendActive());
        resp.setCustomFrontendActive(p.isCustomFrontendActive());

        resp.setDefaultBackendUrl(p.getDefaultBackendUrl());
        resp.setCustomBackendUrl(p.getCustomBackendUrl());
        resp.setDefaultBackendActive(p.isDefaultBackendActive());
        resp.setCustomBackendActive(p.isCustomBackendActive());

        resp.setDefaultMonitoringUrl(p.getDefaultMonitoringUrl());
        resp.setCustomMonitoringUrl(p.getCustomMonitoringUrl());
        resp.setDefaultMonitoringActive(p.isDefaultMonitoringActive());
        resp.setCustomMonitoringActive(p.isCustomMonitoringActive());
        resp.setNeedMonitoringExposed(p.isNeedMonitoringExposed());

        resp.setFrontendListeningPort(p.getFrontendListeningPort());
        resp.setBackendListeningPort(p.getBackendListeningPort());

        resp.setUserId(p.getUserId());
        resp.setCreatedAt(p.getCreatedAt());
        resp.setUpdatedAt(p.getUpdatedAt());

        return resp;
    }

    public static List<ProjectResponse> toProjectListResponse(List<Project> projects) {
        if (projects == null || projects.isEmpty())
            return new ArrayList<>();
        return projects.stream().map(MapperUtils::toProjectResponse).collect(Collectors.toList());
    }

    public static UserResponse toUserResponse(User u) {
        if (u == null)
            return null;

        UserResponse resp = new UserResponse();
        resp.setId(u.getId());
        resp.setUsername(u.getUsername());
        resp.setEmail(u.getEmail());
        resp.setRole(u.getRole());
        resp.setCreatedAt(u.getCreatedAt());
        resp.setUpdatedAt(u.getUpdatedAt());

        return resp;
    }

    public static List<UserResponse> toUserListResponse(List<User> users) {
        if (users == null || users.isEmpty())
            return new ArrayList<>();
        return users.stream().map(MapperUtils::toUserResponse).collect(Collectors.toList());
    }

    public static DeploymentHistoryResponse toDeploymentHistoryResponse(DeploymentHistory d) {
        if (d == null)
            return null;

        DeploymentHistoryResponse resp = new DeploymentHistoryResponse();
        resp.setId(d.getId());
        resp.setProjectId(d.getProjectId());
        resp.setUserId(d.getUserId());
        resp.setStatus(d.getStatus().name().toLowerCase());
        resp.setVersion(d.getVersion());
        resp.setEnvironment(d.getEnvironment());
        resp.setLogs(d.getLogs());
        resp.setStartedAt(d.getStartedAt());
        resp.setFinishedAt(d.getFinishedAt());
        resp.setCreatedAt(d.getCreatedAt());
        resp.setUpdatedAt(d.getUpdatedAt());

        if (d.getProject() != null) {
            resp.setProject(toProjectResponse(d.getProject()));
        }

        if (d.getUser() != null) {
            resp.setUser(toUserResponse(d.getUser()));
        }

        return resp;
    }

    public static List<DeploymentHistoryResponse> toDeploymentHistoryListResponse(List<DeploymentHistory> deployments) {
        if (deployments == null || deployments.isEmpty())
            return new ArrayList<>();
        return deployments.stream().map(MapperUtils::toDeploymentHistoryResponse).collect(Collectors.toList());
    }
}
