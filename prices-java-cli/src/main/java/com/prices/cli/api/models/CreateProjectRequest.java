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
    
    @JsonProperty("custom_frontend_url")
    private String customFrontendURL;
    
    @JsonProperty("custom_backend_url")
    private String customBackendURL;
    
    @JsonProperty("custom_monitoring_url")
    private String customMonitoringURL;
    
    @JsonProperty("need_monitoring_exposed")
    private boolean needMonitoringExposed;
}
