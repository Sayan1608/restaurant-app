package com.restaurantapp.menu;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean available) {

    static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getCategory().getId(), item.getCategory().getName(),
                item.getName(), item.getDescription(), item.getPrice(), item.getImageUrl(), item.isAvailable());
    }
}
