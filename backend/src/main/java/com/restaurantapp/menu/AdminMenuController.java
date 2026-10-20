package com.restaurantapp.menu;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    @PostMapping("/restaurants/{restaurantId}/categories")
    public ResponseEntity<AdminCategoryResponse> createCategory(
            @PathVariable UUID restaurantId, @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminMenuService.createCategory(restaurantId, request));
    }

    @PutMapping("/categories/{categoryId}")
    public AdminCategoryResponse updateCategory(
            @PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return adminMenuService.updateCategory(categoryId, request);
    }

    @PatchMapping("/categories/{categoryId}/availability")
    public AdminCategoryResponse setCategoryActive(
            @PathVariable UUID categoryId, @Valid @RequestBody CategoryActiveRequest request) {
        return adminMenuService.setCategoryActive(categoryId, request.active());
    }

    @PostMapping("/categories/{categoryId}/items")
    public ResponseEntity<MenuItemResponse> createItem(
            @PathVariable UUID categoryId, @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminMenuService.createItem(categoryId, request));
    }

    @PutMapping("/menu-items/{itemId}")
    public MenuItemResponse updateItem(
            @PathVariable UUID itemId, @Valid @RequestBody UpdateMenuItemRequest request) {
        return adminMenuService.updateItem(itemId, request);
    }

    @PatchMapping("/menu-items/{itemId}/availability")
    public MenuItemResponse setItemAvailable(
            @PathVariable UUID itemId, @Valid @RequestBody MenuItemAvailabilityRequest request) {
        return adminMenuService.setItemAvailable(itemId, request.available());
    }
}
