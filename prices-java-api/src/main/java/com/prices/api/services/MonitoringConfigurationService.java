package com.prices.api.services;

import com.prices.api.models.MonitoringConfiguration;

import java.util.List;

public interface MonitoringConfigurationService {
    List<MonitoringConfiguration> getConfigurations(String query, boolean enabledOnly);
}
