package com.miniorch.auth;

import com.miniorch.api.dto.auth.LoginRequest;
import com.miniorch.api.dto.auth.LoginResponse;
import com.miniorch.api.dto.auth.RegisterRequest;
import com.miniorch.api.dto.auth.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException("username already taken: " + request.username());
        }
        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build();
        user = userRepository.save(user);
        log.info("registered user username={}", user.getUsername());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.info("login failed (no such user) username={}", request.username());
                    return new InvalidCredentialsException();
                });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("login failed (bad password) username={}", request.username());
            throw new InvalidCredentialsException();
        }
        JwtTokenProvider.IssuedToken issued = tokenProvider.issue(user);
        log.info("login ok username={} expiresAt={}", user.getUsername(), issued.expiresAt());
        return new LoginResponse(issued.token(), issued.expiresAt(), UserResponse.from(user));
    }
}
