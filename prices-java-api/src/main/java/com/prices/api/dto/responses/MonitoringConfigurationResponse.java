package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

import java.util.List;

@Data
@Serdeable
public class MonitoringConfigurationResponse {
    private Long id;
    private String slug;
    private boolean enabled;
    private List<String> features;
}
