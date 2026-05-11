package com.miniorch.api.dto.auth;

import com.miniorch.auth.User;
import com.miniorch.auth.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, UserRole role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
