package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Serdeable
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private boolean success;
    private String message;
    private String error;

    public static ErrorResponse error(String message) {
        return new ErrorResponse(false, message, null);
    }

    public static ErrorResponse error(String message, String detail) {
        return new ErrorResponse(false, message, detail);
    }
}
