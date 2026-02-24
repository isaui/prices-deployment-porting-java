package com.prices.api.controllers;

import com.prices.api.dto.requests.LoginRequest;
import com.prices.api.dto.requests.RegisterRequest;
import com.prices.api.handlers.AuthHandler;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;

@Slf4j
@Controller("/api/auth")
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
public class AuthController {

    private final AuthHandler handler;

    @Post("/register")
    public HttpResponse<?> register(@Body RegisterRequest req) {
        return handler.register(req);
    }

    @Post("/login")
    public HttpResponse<?> login(@Body LoginRequest req) {
        return handler.login(req);
    }

    @Get("/me")
    public HttpResponse<?> getCurrentUser(Principal principal) {
        log.info("Get current user request for principal: {}", principal != null ? principal.getName() : "null");
        return handler.getCurrentUser(principal);
    }
}
