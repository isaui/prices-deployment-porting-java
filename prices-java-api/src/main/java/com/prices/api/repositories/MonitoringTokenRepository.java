package com.prices.api.repositories;

import com.prices.api.models.MonitoringToken;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoringTokenRepository extends CrudRepository<MonitoringToken, String> {

    List<MonitoringToken> findByProjectSlug(String projectSlug);

    Optional<MonitoringToken> findByIdAndProjectSlug(String id, String projectSlug);
}
