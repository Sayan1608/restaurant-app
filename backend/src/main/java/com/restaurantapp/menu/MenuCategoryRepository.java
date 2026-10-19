package com.restaurantapp.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
}
