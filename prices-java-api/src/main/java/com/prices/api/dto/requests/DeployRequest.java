package com.prices.api.dto.requests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class DeployRequest {
    private Long projectID;
    private Long userID;
    private String version;
    private byte[] artifactData;
}
