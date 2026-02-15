package com.prices.api;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(title = "PRICES Deployment API", version = "1.0", description = "API for managing project deployments and environments"))
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
