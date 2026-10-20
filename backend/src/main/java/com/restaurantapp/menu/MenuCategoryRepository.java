package com.restaurantapp.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    List<MenuCategory> findAllByRestaurant_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    List<MenuCategory> findAllByRestaurant_IdOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    boolean existsByRestaurant_IdAndNameIgnoreCase(UUID restaurantId, String name);
}
