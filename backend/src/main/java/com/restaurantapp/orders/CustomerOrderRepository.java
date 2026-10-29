package com.restaurantapp.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByCustomer_IdAndIdempotencyKey(UUID customerId, UUID idempotencyKey);

    @EntityGraph(attributePaths = "restaurant")
    Page<CustomerOrder> findAllByCustomer_Id(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Page<CustomerOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Page<CustomerOrder> findAllByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);
}
