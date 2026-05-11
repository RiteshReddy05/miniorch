package com.miniorch.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(tokenProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a valid bearer token populates the SecurityContext with ROLE_USER and continues the chain")
    void validToken_populatesContext() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenProvider.parse("good-token"))
                .thenReturn(new JwtTokenProvider.TokenClaims(
                        userId, "alice", UserRole.USER, Instant.now().plusSeconds(3600)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/deployments");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("an invalid bearer token leaves the context empty but still continues the chain")
    void invalidToken_doesNotPopulateContext() throws Exception {
        when(tokenProvider.parse("bad-token")).thenThrow(new JwtException("forged"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/deployments");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("a request with no Authorization header passes through with no context")
    void missingToken_doesNotPopulateContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("an Authorization header without the Bearer prefix is ignored")
    void headerWithoutBearerPrefix_doesNotPopulateContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/deployments");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
