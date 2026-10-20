package com.restaurantapp.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findAllByCategory_Restaurant_IdAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrueAndAvailableTrueOrderByCategory_DisplayOrderAscNameAsc(UUID restaurantId);

    java.util.Optional<MenuItem> findByIdAndAvailableTrueAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrue(UUID id);
}
