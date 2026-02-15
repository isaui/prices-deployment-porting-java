package com.prices.api.services;

import com.prices.api.dto.requests.LoginRequest;
import com.prices.api.dto.requests.RegisterRequest;
import com.prices.api.models.User;

public interface AuthService {
    User register(RegisterRequest req);
    String login(LoginRequest req);
    User validateToken(String token);
}
