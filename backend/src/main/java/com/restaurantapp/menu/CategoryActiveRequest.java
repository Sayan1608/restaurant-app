package com.restaurantapp.menu;

import jakarta.validation.constraints.NotNull;

public record CategoryActiveRequest(@NotNull Boolean active) {
}
