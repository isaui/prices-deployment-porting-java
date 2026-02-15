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
    @JsonProperty("defaultFrontendUrl")
    private String defaultFrontendURL;
    
    @JsonProperty("customFrontendUrl")
    private String customFrontendURL;
    
    @JsonProperty("defaultFrontendActive")
    private boolean isDefaultFrontendActive;
    
    @JsonProperty("customFrontendActive")
    private boolean isCustomFrontendActive;

    // Backend URLs
    @JsonProperty("defaultBackendUrl")
    private String defaultBackendURL;
    
    @JsonProperty("customBackendUrl")
    private String customBackendURL;
    
    @JsonProperty("defaultBackendActive")
    private boolean isDefaultBackendActive;
    
    @JsonProperty("customBackendActive")
    private boolean isCustomBackendActive;

    // Monitoring URLs
    @JsonProperty("defaultMonitoringUrl")
    private String defaultMonitoringURL;
    
    @JsonProperty("customMonitoringUrl")
    private String customMonitoringURL;
    
    @JsonProperty("defaultMonitoringActive")
    private boolean isDefaultMonitoringActive;
    
    @JsonProperty("customMonitoringActive")
    private boolean isCustomMonitoringActive;
    
    @JsonProperty("needMonitoringExposed")
    private boolean needMonitoringExposed;

    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("createdAt")
    private String createdAt;
    
    @JsonProperty("updatedAt")
    private String updatedAt;
}
