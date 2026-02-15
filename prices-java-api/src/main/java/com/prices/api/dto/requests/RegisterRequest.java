package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
}
