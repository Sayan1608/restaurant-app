package com.restaurantapp.config;

import com.restaurantapp.menu.MenuCategory;
import com.restaurantapp.menu.MenuCategoryRepository;
import com.restaurantapp.menu.MenuItem;
import com.restaurantapp.menu.MenuItemRepository;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Component
@Profile("development")
public class DevelopmentDataSeeder implements ApplicationRunner {

    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final TransactionTemplate transactionTemplate;

    public DevelopmentDataSeeder(RestaurantRepository restaurantRepository,
            MenuCategoryRepository categoryRepository, MenuItemRepository menuItemRepository,
            TransactionTemplate transactionTemplate) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> seedIfRestaurantIsMissing());
    }

    private void seedIfRestaurantIsMissing() {
        if (restaurantRepository.existsByNameIgnoreCase("Demo Kitchen")) {
            return;
        }

        Restaurant restaurant = restaurantRepository.save(new Restaurant(
                "Demo Kitchen",
                "Sample menu for local development.",
                "1 Example Street, Bengaluru"));

        MenuCategory mains = categoryRepository.save(new MenuCategory(restaurant, "Mains", 0));
        MenuCategory sides = categoryRepository.save(new MenuCategory(restaurant, "Sides", 1));

        menuItemRepository.save(new MenuItem(mains, "Paneer Tikka Bowl",
                "Grilled paneer with rice and seasonal vegetables.", new BigDecimal("249.00"), null));
        menuItemRepository.save(new MenuItem(mains, "Masala Dosa",
                "Crisp dosa served with sambar and chutney.", new BigDecimal("179.00"), null));
        menuItemRepository.save(new MenuItem(sides, "Lime Soda",
                "Fresh lime with sparkling soda.", new BigDecimal("69.00"), null));
    }
}
