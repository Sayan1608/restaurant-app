package com.restaurantapp.menu;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/menu-items")
public class PublicMenuItemController {

    private final MenuService menuService;

    public PublicMenuItemController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/{itemId}")
    public MenuItemResponse getMenuItem(@PathVariable UUID itemId) {
        return menuService.getPublicItem(itemId);
    }
}
