package com.prices.api.repositories;

import com.prices.api.models.MonitoringConfiguration;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoringConfigurationRepository extends CrudRepository<MonitoringConfiguration, Long> {

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    Optional<MonitoringConfiguration> findByProjectId(Long projectId);

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    List<MonitoringConfiguration> findByProjectIdIn(List<Long> projectIds);

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    List<MonitoringConfiguration> findAll();

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    List<MonitoringConfiguration> findByEnabledTrue();

    void deleteByProjectId(Long projectId);
}
