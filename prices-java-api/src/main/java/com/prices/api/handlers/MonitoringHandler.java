package com.prices.api.handlers;

import com.prices.api.dto.responses.ApiResponse;
import com.prices.api.dto.responses.ErrorResponse;
import com.prices.api.models.MonitoringToken;
import com.prices.api.models.Project;
import com.prices.api.services.MonitoringService;
import com.prices.api.services.ProjectService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.prices.api.constants.Constants.ROLE_ADMIN;

@Singleton
@RequiredArgsConstructor
public class MonitoringHandler {

    private final MonitoringService monitoringService;
    private final ProjectService projectService;

    @Data
    @Serdeable
    public static class CreateTokenRequest {
        private String slug;
    }

    public HttpResponse<?> createToken(String slug, Long userId, String role) {
        try {
            Project project = projectService.getBySlug(slug);
            if (!ROLE_ADMIN.equals(role) && !project.getUserId().equals(userId)) {
                return HttpResponse.status(HttpStatus.FORBIDDEN).body(ErrorResponse.error("Access denied"));
            }

            MonitoringToken token = monitoringService.generateToken(slug);
            String dashboardUrl = monitoringService.buildDashboardUrl(token.getId());

            return HttpResponse.ok(ApiResponse.success("Monitoring token created", Map.of(
                    "token", token.getId(),
                    "url", dashboardUrl,
                    "expiredAt", token.getExpiredAt().toString()
            )));
        } catch (Exception e) {
            return HttpResponse.serverError(ErrorResponse.error("Failed to create token: " + e.getMessage()));
        }
    }
}
