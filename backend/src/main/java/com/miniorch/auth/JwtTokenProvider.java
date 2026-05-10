package com.miniorch.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final SecretKey signingKey;

    public JwtTokenProvider(AuthProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_TTL);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        Instant expiresAt = claims.getExpiration().toInstant();
        return new TokenClaims(userId, username, role, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(UUID userId, String username, UserRole role, Instant expiresAt) {
    }
}
