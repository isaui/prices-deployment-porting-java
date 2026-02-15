package com.prices.cli.api.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvVarDefinition {
    private String key;
    private String description;
    private String example;
}
