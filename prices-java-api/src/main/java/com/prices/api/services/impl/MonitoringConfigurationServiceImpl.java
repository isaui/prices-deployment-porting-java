package com.prices.api.services.impl;

import com.prices.api.models.MonitoringConfiguration;
import com.prices.api.models.Project;
import com.prices.api.repositories.MonitoringConfigurationRepository;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.services.MonitoringConfigurationService;
import io.micronaut.transaction.SynchronousTransactionManager;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor
public class MonitoringConfigurationServiceImpl implements MonitoringConfigurationService {

    private final MonitoringConfigurationRepository monitoringConfigRepo;
    private final ProjectRepository projectRepo;
    private final SynchronousTransactionManager<Connection> txManager;

    @Override
    public List<MonitoringConfiguration> getConfigurations(String query, boolean enabledOnly) {
        if (query == null || query.equalsIgnoreCase("All") || query.equals("$__all")) {
            return enabledOnly
                    ? monitoringConfigRepo.findByEnabledTrue()
                    : monitoringConfigRepo.findAll();
        }

        List<String> slugs = Arrays.stream(query.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<Long> projectIds = projectRepo.findBySlugIn(slugs).stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<MonitoringConfiguration> configs = monitoringConfigRepo.findByProjectIdIn(projectIds);
        if (enabledOnly) {
            configs = configs.stream()
                    .filter(MonitoringConfiguration::isEnabled)
                    .collect(Collectors.toList());
        }
        return configs;
    }

    @Override
    public void upsert(Long projectId, boolean enabled, List<String> features) {
        txManager.executeWrite(status -> {
            Optional<MonitoringConfiguration> existing = monitoringConfigRepo.findByProjectId(projectId);
            if (existing.isPresent()) {
                MonitoringConfiguration config = existing.get();
                config.setEnabled(enabled);
                config.setFeatures(features);
                monitoringConfigRepo.update(config);
            } else {
                MonitoringConfiguration config = new MonitoringConfiguration();
                config.setProjectId(projectId);
                config.setEnabled(enabled);
                config.setFeatures(features);
                monitoringConfigRepo.save(config);
            }
            return null;
        });
    }
}
