package com.restaurantapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    boolean existsByNameIgnoreCase(String name);

    List<Restaurant> findAllByActiveTrueOrderByNameAsc();
}
