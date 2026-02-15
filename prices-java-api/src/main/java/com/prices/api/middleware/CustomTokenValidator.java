package com.prices.api.middleware;

import com.prices.api.models.User;
import com.prices.api.services.AuthService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.validator.TokenValidator;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Singleton
@RequiredArgsConstructor
public class CustomTokenValidator implements TokenValidator {

    private final AuthService authService;

    @Override
    public Publisher<Authentication> validateToken(@NonNull String token, @NonNull Object request) {
        return Mono.create(emitter -> {
            try {
                // Use our existing AuthService which uses JJWT (same logic as Go)
                User user = authService.validateToken(token);
                
                // Create Micronaut Authentication object
                // We use user.getId().toString() as the principal name (userId)
                // This matches controllers: Long.parseLong(principal.getName())
                Authentication auth = Authentication.build(
                    String.valueOf(user.getId()),
                    Collections.singletonMap("user", user) // Add user object to attributes if needed
                );
                
                emitter.success(auth);
            } catch (Exception e) {
                // Token invalid
                emitter.success(); // Empty success means invalid token
            }
        });
    }
}
