package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class UpdateProjectRequest {
    private String name;
    private String description;

    private String customFrontendUrl;
    private String customBackendUrl;
    private String customMonitoringUrl;

    private Boolean isDefaultFrontendActive;
    private Boolean isCustomFrontendActive;

    private Boolean isDefaultBackendActive;
    private Boolean isCustomBackendActive;

    private Boolean isDefaultMonitoringActive;
    private Boolean isCustomMonitoringActive;
    private Boolean needMonitoringExposed;
}
