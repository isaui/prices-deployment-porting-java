package com.prices.api.controllers;

import com.prices.api.dto.requests.UpdateUserRequest;
import com.prices.api.handlers.UserHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.RequiredArgsConstructor;

@Controller("/api/users")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class UserController {

    private final UserHandler handler;

    @Get
    public HttpResponse<?> getAll() {
        return handler.getAll();
    }

    @Get("/{id}")
    public HttpResponse<?> getById(@PathVariable Long id) {
        return handler.getById(id);
    }

    @Put("/{id}")
    public HttpResponse<?> update(@PathVariable Long id, @Body UpdateUserRequest req) {
        return handler.update(id, req);
    }

    @Delete("/{id}")
    public HttpResponse<?> delete(@PathVariable Long id) {
        return handler.delete(id);
    }
}
