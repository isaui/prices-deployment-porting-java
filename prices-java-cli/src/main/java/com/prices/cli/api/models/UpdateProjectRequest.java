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
    @JsonProperty("customFrontendUrl")
    private String customFrontendURL;
    
    @JsonProperty("defaultFrontendActive")
    private Boolean isDefaultFrontendActive;
    
    @JsonProperty("customFrontendActive")
    private Boolean isCustomFrontendActive;

    // Backend
    @JsonProperty("customBackendUrl")
    private String customBackendURL;
    
    @JsonProperty("defaultBackendActive")
    private Boolean isDefaultBackendActive;
    
    @JsonProperty("customBackendActive")
    private Boolean isCustomBackendActive;

    // Monitoring
    @JsonProperty("customMonitoringUrl")
    private String customMonitoringURL;
    
    @JsonProperty("defaultMonitoringActive")
    private Boolean isDefaultMonitoringActive;
    
    @JsonProperty("customMonitoringActive")
    private Boolean isCustomMonitoringActive;
    
    @JsonProperty("needMonitoringExposed")
    private Boolean needMonitoringExposed;

    @JsonProperty("frontendListeningPort")
    private Integer frontendListeningPort;

    @JsonProperty("backendListeningPort")
    private Integer backendListeningPort;
}
