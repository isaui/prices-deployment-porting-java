package com.prices.api.services;

import com.prices.api.dto.requests.UpdateUserRequest;
import com.prices.api.models.Project;
import com.prices.api.models.User;
import java.util.List;

public interface UserService {
    User getById(Long id);

    List<User> getAll();

    User update(Long id, UpdateUserRequest req);

    void delete(Long id);

    List<Project> getProjects(Long userId);
}
