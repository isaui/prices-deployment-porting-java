package com.prices.api.middleware;

import com.prices.api.models.User;
import com.prices.api.services.AuthService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.validator.TokenValidator;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CustomTokenValidator implements TokenValidator {

    private final AuthService authService;

    @Override
    public Publisher<Authentication> validateToken(@NonNull String token, @NonNull Object request) {
        return Mono.create(emitter -> {
            try {
                log.info("Validating token for request: {}", request.getClass().getSimpleName());
                
                // Use our existing AuthService which uses JJWT (same logic as Go)
                User user = authService.validateToken(token);
                
                log.info("Token validation successful for user: {} (ID: {})", user.getUsername(), user.getId());
                
                // Create Micronaut Authentication object
                // We use user.getId().toString() as the principal name (userId)
                // This matches controllers: Long.parseLong(principal.getName())
                Collection<String> roles = Collections.singletonList(user.getRole());
                Map<String, Object> attributes = Map.of(
                    "user", user,
                    "role", user.getRole()
                );
                Authentication auth = Authentication.build(
                    String.valueOf(user.getId()),
                    roles,
                    attributes
                );
                
                emitter.success(auth);
            } catch (Exception e) {
                // Token invalid
                log.warn("Token validation failed: {}", e.getMessage());
                emitter.success(); // Empty success means invalid token
            }
        });
    }
}
