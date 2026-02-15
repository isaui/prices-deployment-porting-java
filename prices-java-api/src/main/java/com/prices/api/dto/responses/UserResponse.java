package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Serdeable
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
