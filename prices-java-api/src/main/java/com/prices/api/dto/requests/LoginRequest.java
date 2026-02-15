package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class LoginRequest {
    private String identifier;
    private String password;
}
