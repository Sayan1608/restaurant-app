package com.restaurantapp.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatMessageRequest(
        @NotNull UUID restaurantId,
        @NotBlank @Size(max = 1000) String message) {
}
