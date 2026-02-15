package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Serdeable
public class ProjectResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String status;

    // Frontend URLs
    private String defaultFrontendUrl;
    private String customFrontendUrl;
    private boolean isDefaultFrontendActive;
    private boolean isCustomFrontendActive;

    // Backend URLs
    private String defaultBackendUrl;
    private String customBackendUrl;
    private boolean isDefaultBackendActive;
    private boolean isCustomBackendActive;

    // Monitoring URLs
    private String defaultMonitoringUrl;
    private String customMonitoringUrl;
    private boolean isDefaultMonitoringActive;
    private boolean isCustomMonitoringActive;
    private boolean needMonitoringExposed;

    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
