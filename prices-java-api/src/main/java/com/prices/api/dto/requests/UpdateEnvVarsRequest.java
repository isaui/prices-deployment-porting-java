package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import java.util.Map;

@Data
@Serdeable
public class UpdateEnvVarsRequest {
    private Map<String, String> envVars;
}
