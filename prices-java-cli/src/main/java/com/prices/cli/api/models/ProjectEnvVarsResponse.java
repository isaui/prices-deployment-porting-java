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
    @JsonProperty("project_id")
    private Long projectId;
    
    @JsonProperty("env_vars")
    private Map<String, String> envVars;
}
