package com.restaurantapp.menu;

import java.util.UUID;

public record AdminCategoryResponse(UUID id, UUID restaurantId, String name, int displayOrder, boolean active) {

    static AdminCategoryResponse from(MenuCategory category) {
        return new AdminCategoryResponse(category.getId(), category.getRestaurant().getId(), category.getName(),
                category.getDisplayOrder(), category.isActive());
    }
}
