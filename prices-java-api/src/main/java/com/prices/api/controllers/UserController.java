package com.prices.api.controllers;

import com.prices.api.dto.requests.UpdateUserRequest;
import com.prices.api.handlers.UserHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

@Controller("/api/users")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class UserController {

    private final UserHandler handler;

    @Get
    public HttpResponse<?> getAll(Authentication auth) {
        String role = (String) auth.getAttributes().get("role");
        return handler.getAll(role);
    }

    @Get("/{id}")
    public HttpResponse<?> getById(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.getById(id, userId, role);
    }

    @Put("/{id}")
    public HttpResponse<?> update(Authentication auth, @PathVariable Long id, @Body UpdateUserRequest req) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.update(id, req, userId, role);
    }

    @Delete("/{id}")
    public HttpResponse<?> delete(Authentication auth, @PathVariable Long id) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.delete(id, userId, role);
    }
}
