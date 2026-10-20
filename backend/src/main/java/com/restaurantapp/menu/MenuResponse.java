package com.restaurantapp.menu;

import java.util.List;
import java.util.UUID;

public record MenuResponse(UUID restaurantId, String restaurantName, List<MenuCategoryResponse> categories) {
}
