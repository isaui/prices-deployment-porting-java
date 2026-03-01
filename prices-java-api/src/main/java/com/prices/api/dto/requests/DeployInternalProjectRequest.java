package com.prices.api.dto.requests;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

import java.util.Map;

@Data
@Introspected
@Serdeable
public class DeployInternalProjectRequest {
    private String version;
    private Map<String, String> envVars;
}
