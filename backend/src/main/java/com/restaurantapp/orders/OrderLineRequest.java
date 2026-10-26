package com.restaurantapp.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderLineRequest(
        @NotNull UUID menuItemId,
        @NotNull @Min(1) @Max(99) Integer quantity) {
}
