package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class CreateProjectRequest {
    private String name;
    private String description;

    private String customFrontendUrl;
    private String customBackendUrl;
    private String customMonitoringUrl;

    private boolean needMonitoringExposed;
}
