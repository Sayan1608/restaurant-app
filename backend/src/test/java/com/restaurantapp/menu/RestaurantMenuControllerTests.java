package com.restaurantapp.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestaurantMenuControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private Restaurant restaurant;
    private MenuCategory mains;
    private MenuItem paneer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        restaurant = restaurantRepository.saveAndFlush(new Restaurant(
                "Restaurant " + suffix, "Test restaurant", "Test address"));
        mains = categoryRepository.saveAndFlush(new MenuCategory(restaurant, "Mains", 0));
        paneer = itemRepository.saveAndFlush(new MenuItem(
                mains, "Paneer Bowl", "Test dish", new BigDecimal("249.00"), null));
    }

    @Test
    void publicRestaurantEndpointsReturnActiveRestaurantAndMenu() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(restaurant.getId())).exists());

        mockMvc.perform(get("/api/restaurants/{id}", restaurant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurant.getId().toString()))
                .andExpect(jsonPath("$.name").value(restaurant.getName()));

        mockMvc.perform(get("/api/restaurants/{id}/menu", restaurant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurant.getId().toString()))
                .andExpect(jsonPath("$.categories[0].name").value("Mains"))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Paneer Bowl"))
                .andExpect(jsonPath("$.categories[0].items[0].price").value(249.00));
    }

    @Test
    void publicMenuOmitsUnavailableItemsAndInactiveCategories() throws Exception {
        MenuItem soda = itemRepository.saveAndFlush(new MenuItem(
                mains, "Lime Soda", null, new BigDecimal("69.00"), null));
        MenuCategory desserts = categoryRepository.saveAndFlush(new MenuCategory(restaurant, "Desserts", 1));
        itemRepository.saveAndFlush(new MenuItem(desserts, "Gulab Jamun", null, new BigDecimal("89.00"), null));
        soda.setAvailable(false);
        desserts.setActive(false);
        itemRepository.flush();
        categoryRepository.flush();

        mockMvc.perform(get("/api/restaurants/{id}/menu", restaurant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].items.length()").value(1))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Paneer Bowl"));

        mockMvc.perform(get("/api/menu-items/{id}", soda.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_ITEM_NOT_FOUND"));
    }

    @Test
    void publicEndpointsHideInactiveRestaurants() throws Exception {
        jdbcTemplate.update("UPDATE restaurants SET active = FALSE WHERE id = ?", restaurant.getId());
        entityManager.clear();

        mockMvc.perform(get("/api/restaurants/{id}", restaurant.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
        mockMvc.perform(get("/api/restaurants/{id}/menu", restaurant.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicItemDetailOnlyReturnsAvailableItemsInActiveRestaurantAndCategory() throws Exception {
        mockMvc.perform(get("/api/menu-items/{id}", paneer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paneer.getId().toString()))
                .andExpect(jsonPath("$.categoryName").value("Mains"))
                .andExpect(jsonPath("$.available").value(true));

        mains.setActive(false);
        categoryRepository.flush();
        mockMvc.perform(get("/api/menu-items/{id}", paneer.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCreateUpdateAndDeactivateCategoryAndItem() throws Exception {
        String categoryBody = objectMapper.writeValueAsString(Map.of("name", "Desserts", "displayOrder", 1));
        String newCategoryId = mockMvc.perform(post("/api/admin/restaurants/{id}/categories", restaurant.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json").content(categoryBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Desserts"))
                .andReturn().getResponse().getContentAsString();
        UUID categoryId = UUID.fromString(objectMapper.readTree(newCategoryId).get("id").asText());

        mockMvc.perform(put("/api/admin/categories/{id}", categoryId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Sweets", "displayOrder", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sweets"))
                .andExpect(jsonPath("$.displayOrder").value(2));

        String itemBody = objectMapper.writeValueAsString(Map.of(
                "name", "Gulab Jamun", "description", "Two pieces", "price", new BigDecimal("89.00")));
        String itemResponse = mockMvc.perform(post("/api/admin/categories/{id}/items", categoryId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json").content(itemBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(objectMapper.readTree(itemResponse).get("id").asText());

        mockMvc.perform(put("/api/admin/menu-items/{id}", itemId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", categoryId, "name", "Gulab Jamun Special",
                                "description", "Three pieces", "price", new BigDecimal("109.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gulab Jamun Special"))
                .andExpect(jsonPath("$.price").value(109.00));

        mockMvc.perform(patch("/api/admin/menu-items/{id}/availability", itemId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json").content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/api/menu-items/{id}", itemId)).andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/categories/{id}/availability", categoryId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminRoutesRejectAnonymousAndCustomerUsers() throws Exception {
        String request = "{\"name\":\"Drinks\",\"displayOrder\":3}";
        mockMvc.perform(post("/api/admin/restaurants/{id}/categories", restaurant.getId())
                        .with(csrf().asHeader()).contentType("application/json").content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/restaurants/{id}/categories", restaurant.getId())
                        .with(user("customer").roles("CUSTOMER")).with(csrf().asHeader())
                        .contentType("application/json").content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWritesRequireCsrfAndValidateFields() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants/{id}/categories", restaurant.getId())
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json").content("{\"name\":\"Drinks\",\"displayOrder\":0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/categories/{id}/items", mains.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void duplicateCategoryNamesReturnConflict() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants/{id}/categories", restaurant.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "mAiNs", "displayOrder", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_UNAVAILABLE"));
    }

    @Test
    void itemCannotBeMovedToAnotherRestaurantCategory() throws Exception {
        Restaurant otherRestaurant = restaurantRepository.saveAndFlush(new Restaurant(
                "Other " + UUID.randomUUID(), null, "Other address"));
        MenuCategory otherCategory = categoryRepository.saveAndFlush(new MenuCategory(otherRestaurant, "Mains", 0));
        String request = objectMapper.writeValueAsString(Map.of(
                "categoryId", otherCategory.getId(), "name", "Paneer Bowl",
                "description", "Test dish", "price", new BigDecimal("249.00")));

        mockMvc.perform(put("/api/admin/menu-items/{id}", paneer.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType("application/json").content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }
}
