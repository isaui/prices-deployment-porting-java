package com.prices.cli.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {
    private String name;
    private String description;
    
    @JsonProperty("customFrontendUrl")
    private String customFrontendURL;
    
    @JsonProperty("customBackendUrl")
    private String customBackendURL;
    
    @JsonProperty("customMonitoringUrl")
    private String customMonitoringURL;
    
    @JsonProperty("needMonitoringExposed")
    private boolean needMonitoringExposed;

    @JsonProperty("frontendListeningPort")
    private Integer frontendListeningPort;

    @JsonProperty("backendListeningPort")
    private Integer backendListeningPort;
}
