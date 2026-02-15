package com.prices.cli.api.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentStatus {
    private String status;
    private String duration;
    private String error;
    private String url;
}
