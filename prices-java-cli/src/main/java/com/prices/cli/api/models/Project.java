package com.prices.cli.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String status;

    // Frontend URLs
    @JsonProperty("default_frontend_url")
    private String defaultFrontendURL;
    
    @JsonProperty("custom_frontend_url")
    private String customFrontendURL;
    
    @JsonProperty("is_default_frontend_active")
    private boolean isDefaultFrontendActive;
    
    @JsonProperty("is_custom_frontend_active")
    private boolean isCustomFrontendActive;

    // Backend URLs
    @JsonProperty("default_backend_url")
    private String defaultBackendURL;
    
    @JsonProperty("custom_backend_url")
    private String customBackendURL;
    
    @JsonProperty("is_default_backend_active")
    private boolean isDefaultBackendActive;
    
    @JsonProperty("is_custom_backend_active")
    private boolean isCustomBackendActive;

    // Monitoring URLs
    @JsonProperty("default_monitoring_url")
    private String defaultMonitoringURL;
    
    @JsonProperty("custom_monitoring_url")
    private String customMonitoringURL;
    
    @JsonProperty("is_default_monitoring_active")
    private boolean isDefaultMonitoringActive;
    
    @JsonProperty("is_custom_monitoring_active")
    private boolean isCustomMonitoringActive;
    
    @JsonProperty("need_monitoring_exposed")
    private boolean needMonitoringExposed;

    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("updated_at")
    private String updatedAt;
}
