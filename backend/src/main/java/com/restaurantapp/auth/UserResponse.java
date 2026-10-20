package com.restaurantapp.auth;

import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, UserRole role, Instant createdAt) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getName(), account.getEmail(),
                account.getRole(), account.getCreatedAt());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
