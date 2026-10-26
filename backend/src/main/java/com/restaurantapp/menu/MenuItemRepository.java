package com.restaurantapp.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findAllByCategory_Restaurant_IdAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrueAndAvailableTrueOrderByCategory_DisplayOrderAscNameAsc(UUID restaurantId);

    java.util.Optional<MenuItem> findByIdAndAvailableTrueAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrue(UUID id);

    @Query("""
            select item from MenuItem item
            join fetch item.category category
            join fetch category.restaurant restaurant
            where item.id in :itemIds
              and restaurant.id = :restaurantId
              and item.available = true
              and category.active = true
              and restaurant.active = true
            """)
    List<MenuItem> findAvailableForCheckout(@Param("restaurantId") UUID restaurantId,
            @Param("itemIds") Collection<UUID> itemIds);
}
