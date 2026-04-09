package com.prices.api.services.impl;

import com.prices.api.models.MonitoringConfiguration;
import com.prices.api.models.Project;
import com.prices.api.repositories.MonitoringConfigurationRepository;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.services.MonitoringConfigurationService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MonitoringConfigurationServiceImpl implements MonitoringConfigurationService {

    private final MonitoringConfigurationRepository monitoringConfigRepo;
    private final ProjectRepository projectRepo;

    @Override
    public List<MonitoringConfiguration> getConfigurations(String query, boolean enabledOnly) {
        if (query == null || query.equalsIgnoreCase("All") || query.equals("$__all") || query.equals(".*")) {
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
    public List<String> getProductLines(boolean enabledOnly) {
        List<MonitoringConfiguration> configs = getConfigurations("All", enabledOnly);
        return configs.stream()
                .map(c -> c.getProject() != null ? c.getProject().getProductLine() : null)
                .filter(pl -> pl != null && !pl.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getSlugs(String query, String productLine, boolean enabledOnly) {
        List<MonitoringConfiguration> configs = getConfigurations(query, enabledOnly);
        boolean filterByProductLine = productLine != null
                && !productLine.isEmpty()
                && !productLine.equalsIgnoreCase("All")
                && !productLine.equals("$__all")
                && !productLine.equals(".*");
        return configs.stream()
                .filter(c -> {
                    if (!filterByProductLine) {
                        return true;
                    }
                    if (c.getProject() == null || c.getProject().getProductLine() == null) {
                        return false;
                    }
                    // Support comma-separated product lines from Grafana multi-select
                    for (String pl : productLine.split(",")) {
                        if (pl.trim().equals(c.getProject().getProductLine())) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(c -> c.getProject() != null ? c.getProject().getSlug() : null)
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getFeatures(String query, boolean enabledOnly) {
        List<MonitoringConfiguration> configs = getConfigurations(query, enabledOnly);
        Set<String> features = new LinkedHashSet<>();
        for (MonitoringConfiguration c : configs) {
            if (c.getFeatures() != null) features.addAll(c.getFeatures());
        }
        return List.copyOf(features);
    }

    @Override
    public void upsert(Long projectId, boolean enabled, List<String> features) {
        Optional<MonitoringConfiguration> existing = monitoringConfigRepo.findByProjectId(projectId);
        if (existing.isPresent()) {
            MonitoringConfiguration config = existing.get();
            config.setEnabled(enabled);
            config.setFeatures(features);
            monitoringConfigRepo.update(config);
            log.info("Updated monitoring config: projectId={}", projectId);
        } else {
            MonitoringConfiguration config = new MonitoringConfiguration();
            config.setProjectId(projectId);
            config.setEnabled(enabled);
            config.setFeatures(features);
            MonitoringConfiguration saved = monitoringConfigRepo.save(config);
            log.info("Saved new monitoring config: projectId={}, id={}", projectId, saved.getId());
        }
    }
}
