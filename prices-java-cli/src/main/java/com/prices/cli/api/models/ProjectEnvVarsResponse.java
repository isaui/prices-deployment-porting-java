package com.prices.cli.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEnvVarsResponse {
    @JsonProperty("projectId")
    private Long projectId;
    
    @JsonProperty("envVars")
    private Map<String, String> envVars;
}
