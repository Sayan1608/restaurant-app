package com.restaurantapp.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByCustomer_IdAndIdempotencyKey(UUID customerId, UUID idempotencyKey);
}
