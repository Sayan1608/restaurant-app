package com.restaurantapp.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrder_IdOrderById(UUID orderId);

    @Query("select item from OrderItem item where item.order.id in :orderIds order by item.order.createdAt desc, item.id asc")
    List<OrderItem> findAllForOrders(@Param("orderIds") Collection<UUID> orderIds);
}
