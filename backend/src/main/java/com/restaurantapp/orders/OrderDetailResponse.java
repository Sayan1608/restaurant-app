package com.restaurantapp.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID id,
        UUID restaurantId,
        UUID customerId,
        String customerName,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal tax,
        BigDecimal total,
        String contactName,
        String contactPhone,
        String deliveryAddress,
        String deliveryNotes,
        Instant createdAt,
        Instant updatedAt) {

    static OrderDetailResponse from(CustomerOrder order, List<OrderItem> items) {
        return new OrderDetailResponse(order.getId(), order.getRestaurant().getId(),
                order.getCustomer().getId(), order.getCustomer().getName(), order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(), order.getSubtotal(),
                order.getDeliveryFee(), order.getTax(), order.getTotal(), order.getContactName(),
                order.getContactPhone(), order.getDeliveryAddress(), order.getDeliveryNotes(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
