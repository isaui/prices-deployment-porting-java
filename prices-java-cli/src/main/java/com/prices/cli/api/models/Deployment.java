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
    private String error;
    private String version;
    
    @JsonProperty("startedAt")
    private String startedAt;
    
    @JsonProperty("finishedAt")
    private String finishedAt;
    
    @JsonProperty("createdAt")
    private String createdAt;
    
    private Project project;
    
    public String getDuration() {
        if (startedAt == null || finishedAt == null) {
            return "-";
        }
        try {
            java.time.LocalDateTime start = java.time.LocalDateTime.parse(startedAt);
            java.time.LocalDateTime end = java.time.LocalDateTime.parse(finishedAt);
            long seconds = java.time.Duration.between(start, end).getSeconds();
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                return (seconds / 60) + "m " + (seconds % 60) + "s";
            } else {
                return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
            }
        } catch (Exception e) {
            return "-";
        }
    }
}
