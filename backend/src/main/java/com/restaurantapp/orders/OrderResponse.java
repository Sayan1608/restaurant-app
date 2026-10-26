package com.restaurantapp.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID restaurantId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal tax,
        BigDecimal total,
        Instant createdAt) {

    static OrderResponse from(CustomerOrder order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getRestaurant().getId(), order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(), order.getSubtotal(),
                order.getDeliveryFee(), order.getTax(), order.getTotal(), order.getCreatedAt());
    }
}
