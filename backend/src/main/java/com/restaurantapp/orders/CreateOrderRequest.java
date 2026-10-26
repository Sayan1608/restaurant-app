package com.restaurantapp.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID restaurantId,
        @NotEmpty @Size(max = 50) List<@Valid OrderLineRequest> items,
        @NotBlank @Size(max = 120) String contactName,
        @NotBlank @Size(max = 32) String contactPhone,
        @NotBlank @Size(max = 1000) String deliveryAddress,
        @Size(max = 500) String deliveryNotes) {
}
