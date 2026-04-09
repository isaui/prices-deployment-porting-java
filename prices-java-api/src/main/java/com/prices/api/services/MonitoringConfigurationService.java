package com.prices.api.services;

import com.prices.api.models.MonitoringConfiguration;

import java.util.List;

public interface MonitoringConfigurationService {
    List<MonitoringConfiguration> getConfigurations(String query, boolean enabledOnly);

    List<String> getProductLines(boolean enabledOnly);

    List<String> getSlugs(String query, String productLine, boolean enabledOnly);

    List<String> getFeatures(String query, boolean enabledOnly);

    void upsert(Long projectId, boolean enabled, List<String> features);
}
