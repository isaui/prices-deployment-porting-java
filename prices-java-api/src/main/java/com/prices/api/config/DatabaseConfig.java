package com.prices.api.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties("database.external")
@Data
public class DatabaseConfig {
    private String host = "10.119.106.82";
    private int port = 5432;
    private String deployerUser = "prices";
    private String deployerPassword = "prices123";
}
