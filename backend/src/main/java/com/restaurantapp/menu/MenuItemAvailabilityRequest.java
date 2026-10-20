package com.restaurantapp.menu;

import jakarta.validation.constraints.NotNull;

public record MenuItemAvailabilityRequest(@NotNull Boolean available) {
}
