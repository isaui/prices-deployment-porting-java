package com.prices.api.services.impl;

import com.prices.api.dto.requests.LoginRequest;
import com.prices.api.dto.requests.RegisterRequest;
import com.prices.api.models.User;
import com.prices.api.repositories.UserRepository;
import com.prices.api.services.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepo;

    // TODO: Move to configuration
    private static final String JWT_SECRET = System.getenv("JWT_SECRET") != null ? System.getenv("JWT_SECRET") : "your-secret-key-here-must-be-at-least-256-bits-long-for-hs256";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    @Override
    @Transactional
    public User register(RegisterRequest req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepo.findByUsername(req.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }

        String hashedPassword = hashPassword(req.getPassword());

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(hashedPassword);
        user.setRole("developer");

        return userRepo.save(user);
    }

    @Override
    public String login(LoginRequest req) {
        Optional<User> userOpt = userRepo.findByEmail(req.getIdentifier());
        if (userOpt.isEmpty()) {
            userOpt = userRepo.findByUsername(req.getIdentifier());
            if (userOpt.isEmpty()) {
                throw new RuntimeException("Invalid credentials");
            }
        }

        User user = userOpt.get();
        if (!checkPassword(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return generateToken(user);
    }

    @Override
    public User validateToken(String token) {
        Claims claims = parseToken(token);
        // jwt-java usually stores numbers as Integer or Long depending on size, but claims.get returns Object.
        // We stored user_id as Long.
        Number userIdNum = claims.get("user_id", Number.class);
        if (userIdNum == null) {
             throw new RuntimeException("Invalid token claims");
        }
        Long userId = userIdNum.longValue();

        return userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private boolean checkPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

    private String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.builder()
                .claim("user_id", user.getId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }

    private Claims parseToken(String tokenString) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(tokenString)
                .getPayload();
    }
}
