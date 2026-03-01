package com.prices.api.dto.requests;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Introspected
@Serdeable
public class CreateInternalProjectRequest {
    private String name;
    private String customFrontendUrl;
    private String customBackendUrl;
    private Integer frontendListeningPort;
    private Integer backendListeningPort;
}
