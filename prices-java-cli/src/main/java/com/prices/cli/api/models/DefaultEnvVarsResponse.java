package com.prices.cli.api.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefaultEnvVarsResponse {
    private List<EnvVarDefinition> backend;
    private List<EnvVarDefinition> frontend;
}
