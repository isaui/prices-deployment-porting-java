package com.prices.api.handlers;

import com.prices.api.dto.requests.LoginRequest;
import com.prices.api.dto.requests.RegisterRequest;
import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.dto.responses.LoginResponse;
import com.prices.api.dto.responses.UserResponse;
import com.prices.api.models.User;
import com.prices.api.services.AuthService;
import com.prices.api.utils.MapperUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class AuthHandler {

    private final AuthService authService;

    public HttpResponse<?> register(RegisterRequest req) {
        if (req.getUsername() == null || req.getEmail() == null || req.getPassword() == null) {
            return HttpResponse.badRequest(ErrorResponse.error("Username, email and password are required"));
        }

        try {
            User user = authService.register(req);
            UserResponse userResp = MapperUtils.toUserResponse(user);
            return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success("User registered successfully", userResp));
        } catch (Exception e) {
            return HttpResponse.badRequest(ErrorResponse.error(e.getMessage()));
        }
    }

    public HttpResponse<?> login(LoginRequest req) {
        if (req.getIdentifier() == null || req.getPassword() == null) {
            return HttpResponse.badRequest(ErrorResponse.error("Username/email and password are required"));
        }

        try {
            String token = authService.login(req);
            return HttpResponse.ok(ApiResponse.success("Login successful", new LoginResponse(token, null)));
        } catch (Exception e) {
            return HttpResponse.unauthorized().body(ErrorResponse.error(e.getMessage()));
        }
    }
}
