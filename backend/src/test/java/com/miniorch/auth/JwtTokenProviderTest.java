package com.miniorch.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-thirty-two-characters-long-padding";

    private final JwtTokenProvider provider = new JwtTokenProvider(new AuthProperties(SECRET));

    @Test
    @DisplayName("generated tokens round-trip back to the same claims")
    void generate_and_parse_roundTrip() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .username("alice")
                .passwordHash("ignored")
                .role(UserRole.USER)
                .createdAt(Instant.now())
                .build();

        JwtTokenProvider.IssuedToken issued = provider.issue(user);
        JwtTokenProvider.TokenClaims parsed = provider.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(id);
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.role()).isEqualTo(UserRole.USER);
        assertThat(parsed.expiresAt()).isEqualTo(issued.expiresAt());
        assertThat(Duration.between(Instant.now(), issued.expiresAt())).isCloseTo(
                JwtTokenProvider.TOKEN_TTL, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("an expired token is rejected with ExpiredJwtException")
    void parse_expiredToken_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minus(Duration.ofMinutes(5));
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "alice")
                .claim("role", UserRole.USER.name())
                .issuedAt(Date.from(past.minus(Duration.ofMinutes(1))))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> provider.parse(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a tampered signature is rejected with SignatureException")
    void parse_tamperedToken_throws() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .role(UserRole.USER)
                .build();
        String token = provider.issue(user).token();
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        char swapped = (first == 'A') ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + swapped + parts[2].substring(1);

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(SignatureException.class);
    }
}
