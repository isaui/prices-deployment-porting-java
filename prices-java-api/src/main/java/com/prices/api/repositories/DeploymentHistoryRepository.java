package com.prices.api.repositories;

import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.DeploymentStatus;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentHistoryRepository extends CrudRepository<DeploymentHistory, Long> {

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    Optional<DeploymentHistory> findById(Long id);

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    List<DeploymentHistory> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    List<DeploymentHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DeploymentHistory> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<DeploymentHistory> findFirstByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, DeploymentStatus status);

    @Join(value = "project", type = Join.Type.LEFT_FETCH)
    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    List<DeploymentHistory> findAllOrderByCreatedAtDesc();
}
