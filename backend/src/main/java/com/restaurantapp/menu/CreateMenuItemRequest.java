package com.restaurantapp.menu;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMenuItemRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 2048) String imageUrl) {
}
