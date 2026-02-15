package com.prices.api.repositories;

import com.prices.api.models.Project;
import io.micronaut.data.annotation.Join;
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

    @Join(value = "user", type = Join.Type.LEFT_FETCH)
    List<Project> findAll();
}
