package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class UpdateUserRequest {
    private String username;
    private String email;
}
