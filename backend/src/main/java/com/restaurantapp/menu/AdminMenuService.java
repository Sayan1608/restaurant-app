package com.restaurantapp.menu;

import com.restaurantapp.common.ApiException;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminMenuService {

    private final RestaurantService restaurantService;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    public AdminMenuService(RestaurantService restaurantService,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository) {
        this.restaurantService = restaurantService;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public AdminCategoryResponse createCategory(UUID restaurantId, CreateCategoryRequest request) {
        Restaurant restaurant = restaurantService.findActive(restaurantId);
        if (categoryRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurantId, request.name().trim())) {
            throw categoryConflict();
        }
        try {
            MenuCategory category = categoryRepository.saveAndFlush(
                    new MenuCategory(restaurant, request.name(), request.displayOrder()));
            return AdminCategoryResponse.from(category);
        } catch (DataIntegrityViolationException exception) {
            throw categoryConflict();
        }
    }

    @Transactional
    public AdminCategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        MenuCategory category = findCategory(categoryId);
        String name = request.name().trim();
        boolean duplicate = categoryRepository.existsByRestaurant_IdAndNameIgnoreCase(
                category.getRestaurant().getId(), name);
        if (duplicate && !category.getName().equalsIgnoreCase(name)) {
            throw categoryConflict();
        }
        category.update(name, request.displayOrder());
        try {
            categoryRepository.flush();
            return AdminCategoryResponse.from(category);
        } catch (DataIntegrityViolationException exception) {
            throw categoryConflict();
        }
    }

    @Transactional
    public AdminCategoryResponse setCategoryActive(UUID categoryId, boolean active) {
        MenuCategory category = findCategory(categoryId);
        category.setActive(active);
        return AdminCategoryResponse.from(category);
    }

    @Transactional
    public MenuItemResponse createItem(UUID categoryId, CreateMenuItemRequest request) {
        MenuCategory category = findActiveCategory(categoryId);
        MenuItem item = itemRepository.saveAndFlush(new MenuItem(category, request.name(), request.description(),
                request.price(), normalizeImageUrl(request.imageUrl())));
        return MenuItemResponse.from(item);
    }

    @Transactional
    public MenuItemResponse updateItem(UUID itemId, UpdateMenuItemRequest request) {
        MenuItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> itemNotFound());
        MenuCategory category = findActiveCategory(request.categoryId());
        if (!category.getRestaurant().getId().equals(item.getCategory().getRestaurant().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY",
                    "Menu item must belong to a category in the same restaurant.");
        }
        item.update(category, request.name(), request.description(), request.price(), normalizeImageUrl(request.imageUrl()));
        return MenuItemResponse.from(item);
    }

    @Transactional
    public MenuItemResponse setItemAvailable(UUID itemId, boolean available) {
        MenuItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> itemNotFound());
        item.setAvailable(available);
        return MenuItemResponse.from(item);
    }

    private MenuCategory findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Menu category was not found."));
    }

    private MenuCategory findActiveCategory(UUID categoryId) {
        MenuCategory category = findCategory(categoryId);
        if (!category.isActive() || !category.getRestaurant().isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_INACTIVE",
                    "Menu items can only be changed in an active category.");
        }
        return category;
    }

    private String normalizeImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }

    private ApiException categoryConflict() {
        return new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_UNAVAILABLE",
                "A category with that name already exists for this restaurant.");
    }

    private ApiException itemNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MENU_ITEM_NOT_FOUND", "Menu item was not found.");
    }
}
