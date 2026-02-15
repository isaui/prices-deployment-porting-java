package com.prices.cli.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequest {
    private String name;
    private String description;

    // Frontend
    @JsonProperty("custom_frontend_url")
    private String customFrontendURL;
    
    @JsonProperty("is_default_frontend_active")
    private Boolean isDefaultFrontendActive;
    
    @JsonProperty("is_custom_frontend_active")
    private Boolean isCustomFrontendActive;

    // Backend
    @JsonProperty("custom_backend_url")
    private String customBackendURL;
    
    @JsonProperty("is_default_backend_active")
    private Boolean isDefaultBackendActive;
    
    @JsonProperty("is_custom_backend_active")
    private Boolean isCustomBackendActive;

    // Monitoring
    @JsonProperty("custom_monitoring_url")
    private String customMonitoringURL;
    
    @JsonProperty("is_default_monitoring_active")
    private Boolean isDefaultMonitoringActive;
    
    @JsonProperty("is_custom_monitoring_active")
    private Boolean isCustomMonitoringActive;
    
    @JsonProperty("need_monitoring_exposed")
    private Boolean needMonitoringExposed;
}
