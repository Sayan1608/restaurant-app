package com.restaurantapp.restaurant;

import com.restaurantapp.menu.MenuResponse;
import com.restaurantapp.menu.MenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MenuService menuService;

    public RestaurantController(RestaurantService restaurantService, MenuService menuService) {
        this.restaurantService = restaurantService;
        this.menuService = menuService;
    }

    @GetMapping
    public List<RestaurantResponse> listRestaurants() {
        return restaurantService.listActive();
    }

    @GetMapping("/{restaurantId}")
    public RestaurantResponse getRestaurant(@PathVariable UUID restaurantId) {
        return restaurantService.getActive(restaurantId);
    }

    @GetMapping("/{restaurantId}/menu")
    public MenuResponse getMenu(@PathVariable UUID restaurantId) {
        return menuService.getPublicMenu(restaurantId);
    }
}
