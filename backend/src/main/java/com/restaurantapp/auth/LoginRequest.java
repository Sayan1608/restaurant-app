package com.restaurantapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 320, message = "Email must be 320 characters or fewer")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be 72 characters or fewer")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
