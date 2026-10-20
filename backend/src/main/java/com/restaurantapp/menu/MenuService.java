package com.restaurantapp.menu;

import com.restaurantapp.common.ApiException;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final RestaurantService restaurantService;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    public MenuService(RestaurantService restaurantService,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository) {
        this.restaurantService = restaurantService;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public MenuResponse getPublicMenu(UUID restaurantId) {
        Restaurant restaurant = restaurantService.findActive(restaurantId);
        List<MenuCategory> categories = categoryRepository
                .findAllByRestaurant_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(restaurantId);
        Map<UUID, List<MenuItemResponse>> itemsByCategory = itemRepository
                .findAllByCategory_Restaurant_IdAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrueAndAvailableTrueOrderByCategory_DisplayOrderAscNameAsc(restaurantId)
                .stream()
                .map(MenuItemResponse::from)
                .collect(Collectors.groupingBy(MenuItemResponse::categoryId));

        List<MenuCategoryResponse> categoryResponses = categories.stream()
                .map(category -> new MenuCategoryResponse(category.getId(), category.getName(),
                        category.getDisplayOrder(), itemsByCategory.getOrDefault(category.getId(), List.of())))
                .toList();
        return new MenuResponse(restaurant.getId(), restaurant.getName(), categoryResponses);
    }

    @Transactional(readOnly = true)
    public MenuItemResponse getPublicItem(UUID itemId) {
        MenuItem item = itemRepository
                .findByIdAndAvailableTrueAndCategory_ActiveTrueAndCategory_Restaurant_ActiveTrue(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_ITEM_NOT_FOUND",
                        "Menu item was not found."));
        return MenuItemResponse.from(item);
    }
}
