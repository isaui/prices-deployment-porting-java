package com.prices.api.handlers;

import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.dto.responses.MonitoringConfigurationListResponse;
import com.prices.api.dto.responses.MonitoringConfigurationResponse;
import com.prices.api.models.MonitoringConfiguration;
import com.prices.api.services.MonitoringConfigurationService;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor
public class MonitoringConfigurationHandler {

    private final MonitoringConfigurationService monitoringConfigurationService;

    public HttpResponse<?> getServices(String query, boolean enabledOnly) {
        try {
            List<MonitoringConfiguration> configs = monitoringConfigurationService
                    .getConfigurations(query, enabledOnly);

            List<MonitoringConfigurationResponse> responses = configs.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            return HttpResponse.ok(ApiResponse.success(
                    new MonitoringConfigurationListResponse(responses)));
        } catch (Exception e) {
            return HttpResponse.serverError(
                    ErrorResponse.error("Failed to fetch monitoring configurations: " + e.getMessage()));
        }
    }

    public HttpResponse<?> getServiceSlugs(String query, boolean enabledOnly) {
        try {
            List<MonitoringConfiguration> configs = monitoringConfigurationService
                    .getConfigurations(query, enabledOnly);
            List<String> slugs = configs.stream()
                    .map(c -> c.getProject() != null ? c.getProject().getSlug() : null)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
            return HttpResponse.ok(slugs);
        } catch (Exception e) {
            return HttpResponse.serverError(
                    ErrorResponse.error("Failed to fetch slugs: " + e.getMessage()));
        }
    }

    public HttpResponse<?> getServiceFeatures(String query, boolean enabledOnly) {
        try {
            List<MonitoringConfiguration> configs = monitoringConfigurationService
                    .getConfigurations(query, enabledOnly);
            Set<String> features = new LinkedHashSet<>();
            for (MonitoringConfiguration c : configs) {
                if (c.getFeatures() != null) features.addAll(c.getFeatures());
            }
            return HttpResponse.ok(List.copyOf(features));
        } catch (Exception e) {
            return HttpResponse.serverError(
                    ErrorResponse.error("Failed to fetch features: " + e.getMessage()));
        }
    }

    private MonitoringConfigurationResponse toResponse(MonitoringConfiguration config) {
        MonitoringConfigurationResponse response = new MonitoringConfigurationResponse();
        response.setId(config.getId());
        response.setSlug(config.getProject() != null ? config.getProject().getSlug() : null);
        response.setEnabled(config.isEnabled());
        response.setFeatures(config.getFeatures());
        return response;
    }
}
