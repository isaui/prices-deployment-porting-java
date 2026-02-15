package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Serdeable
public class DeploymentHistoryResponse {
    private Long id;
    private Long projectId;
    private Long userId;
    private String status;
    private String version;
    private String environment;
    private String logs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProjectResponse project;
    private UserResponse user;
}
