package com.prices.cli.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Deployment {
    private Long id;
    private String status;
    private String duration;
    private String error;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    private Project project;
}
