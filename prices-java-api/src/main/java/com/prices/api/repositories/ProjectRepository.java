package com.prices.api.repositories;

import com.prices.api.models.Project;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends CrudRepository<Project, Long> {

    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    Optional<Project> findById(Long id);

    List<Project> findByUserId(Long userId);

    Optional<Project> findByName(String name);

    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    Optional<Project> findBySlug(String slug);

    List<Project> findBySlugIn(List<String> slugs);

    @Query("""
            SELECT p FROM Project p
            WHERE (:currentProjectId IS NULL OR p.id <> :currentProjectId)
            AND (
                p.defaultFrontendUrl = :domain OR
                p.customFrontendUrl = :domain OR
                p.defaultBackendUrl = :domain OR
                p.customBackendUrl = :domain
            )
            """)
    Optional<Project> findAnyProjectUsingUrl(String domain, Long currentProjectId);

    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    List<Project> findAll();
}
