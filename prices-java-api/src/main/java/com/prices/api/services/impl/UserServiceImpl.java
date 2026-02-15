package com.prices.api.services.impl;

import com.prices.api.dto.requests.UpdateUserRequest;
import com.prices.api.models.Project;
import com.prices.api.models.User;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.repositories.UserRepository;
import com.prices.api.services.UserService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.StreamSupport;

@Singleton
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepo;
    private final ProjectRepository projectRepo;

    @Override
    public User getById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    @Override
    public List<User> getAll() {
        return StreamSupport.stream(userRepo.findAll().spliterator(), false).toList();
    }

    @Override
    @Transactional
    public User update(Long id, UpdateUserRequest req) {
        User user = userRepo.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        if (req.getUsername() != null && !req.getUsername().isEmpty()) {
            user.setUsername(req.getUsername());
        }
        if (req.getEmail() != null && !req.getEmail().isEmpty()) {
            user.setEmail(req.getEmail());
        }

        return userRepo.update(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        userRepo.deleteById(id);
    }

    @Override
    public List<Project> getProjects(Long userId) {
        return projectRepo.findByUserId(userId);
    }
}
