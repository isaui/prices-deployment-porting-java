package com.prices.api.middleware;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static com.prices.api.constants.Constants.INTERNAL_API_KEY_HEADER;

/**
 * Filter for internal API endpoints that require API key authentication.
 * Used for deploy/ssh endpoints accessed from IDE.
 */
@Slf4j
@Singleton
@Filter("/api/internal/**")
public class InternalApiKeyFilter implements HttpServerFilter {

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // Validate API key is configured
        if (internalApiKey == null || internalApiKey.isBlank()) {
            log.error("INTERNAL_API_KEY not configured");
            return Mono.just(HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal API not configured\"}"));
        }

        // Get API key from header
        String providedKey = request.getHeaders().get(INTERNAL_API_KEY_HEADER);
        
        if (providedKey == null || providedKey.isBlank()) {
            log.warn("Missing API key header for internal endpoint: {}", request.getPath());
            return Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Missing X-Internal-Api-Key header\"}"));
        }

        // Validate API key
        if (!internalApiKey.equals(providedKey)) {
            log.warn("Invalid API key for internal endpoint: {}", request.getPath());
            return Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Invalid API key\"}"));
        }

        log.debug("Internal API key validated for: {}", request.getPath());
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return -100; // Run before other filters
    }
}
