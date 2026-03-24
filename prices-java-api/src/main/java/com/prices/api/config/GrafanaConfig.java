package com.prices.api.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties("grafana")
@Data
public class GrafanaConfig {
    private String url = "http://grafana:3000";
    private String serviceAccountToken;
    private String dashboardUid = "app-deployment-monitoring";
    private int tokenExpiryHours = 24;
    private String publicBaseUrl = "";
    private String prometheusUrl = "http://prometheus:9090";
}
