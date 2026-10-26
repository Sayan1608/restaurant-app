package com.restaurantapp.orders;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID menuItemId,
        String itemName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getMenuItem() == null ? null : item.getMenuItem().getId(),
                item.getItemNameSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity(), item.getLineTotal());
    }
}
