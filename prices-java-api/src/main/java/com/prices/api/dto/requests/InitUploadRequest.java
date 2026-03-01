package com.prices.api.dto.requests;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Introspected
@Serdeable
public class InitUploadRequest {
    private String projectSlug;
    private String fileName;
    private long totalSize;
    private int totalChunks;
}
