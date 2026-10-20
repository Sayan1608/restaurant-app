package com.restaurantapp.restaurant;

import java.time.Instant;
import java.util.UUID;

public record RestaurantResponse(UUID id, String name, String description, String address, Instant createdAt) {

    static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getDescription(),
                restaurant.getAddress(), restaurant.getCreatedAt());
    }
}
