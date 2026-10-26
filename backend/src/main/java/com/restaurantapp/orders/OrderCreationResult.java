package com.restaurantapp.orders;

public record OrderCreationResult(OrderResponse order, boolean replayed) {
}
