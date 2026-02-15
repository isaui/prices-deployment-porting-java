package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Serdeable
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEnvVarsResponse {
    private Long projectId;
    private Map<String, String> envVars;
}
