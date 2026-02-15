package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import java.util.Map;

@Data
@Serdeable
public class DeployRequest {
    private Long projectID;
    private Long userID;
    private String version;
    private byte[] artifactData;
    private Map<String, String> inputEnvVars;
}
