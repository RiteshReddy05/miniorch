package com.miniorch.auth;

import com.miniorch.api.dto.auth.LoginRequest;
import com.miniorch.api.dto.auth.LoginResponse;
import com.miniorch.api.dto.auth.RegisterRequest;
import com.miniorch.api.dto.auth.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register persists a USER with a bcrypt-hashed password")
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("alicepass123")).thenReturn("$2a$10$hashedhashedhashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            if (u.getCreatedAt() == null) {
                u.setCreatedAt(Instant.now());
            }
            return u;
        });

        UserResponse response = authService.register(new RegisterRequest("alice", "alicepass123"));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(UserRole.USER);
        assertThat(response.id()).isNotNull();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedhashedhashed");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("register throws UsernameAlreadyExistsException for duplicate usernames")
    void register_duplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "alicepass123")))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("login returns a JWT and the user record on a correct password")
    void login_success() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .username("alice")
                .passwordHash("$2a$10$hashed")
                .role(UserRole.USER)
                .createdAt(Instant.now())
                .build();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("alicepass123", "$2a$10$hashed")).thenReturn(true);
        when(tokenProvider.issue(user)).thenReturn(new JwtTokenProvider.IssuedToken("the-token", expiresAt));

        LoginResponse response = authService.login(new LoginRequest("alice", "alicepass123"));

        assertThat(response.token()).isEqualTo("the-token");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.user().id()).isEqualTo(id);
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException on a wrong password")
    void login_wrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .passwordHash("$2a$10$hashed")
                .role(UserRole.USER)
                .createdAt(Instant.now())
                .build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("nope", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "nope")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenProvider, never()).issue(any());
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException for a non-existent user")
    void login_nonExistentUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "anything")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(tokenProvider, never()).issue(any());
    }
}
