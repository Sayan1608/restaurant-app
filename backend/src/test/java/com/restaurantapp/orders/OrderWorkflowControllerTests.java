package com.restaurantapp.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantapp.auth.AuthenticatedUser;
import com.restaurantapp.menu.MenuCategory;
import com.restaurantapp.menu.MenuCategoryRepository;
import com.restaurantapp.menu.MenuItem;
import com.restaurantapp.menu.MenuItemRepository;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantRepository;
import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import com.restaurantapp.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderWorkflowControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuCategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private CustomerOrderRepository orderRepository;

    private Restaurant restaurant;
    private MenuItem item;
    private UserAccount owner;
    private UserAccount otherCustomer;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        restaurant = restaurantRepository.saveAndFlush(new Restaurant("Workflow " + UUID.randomUUID(), null, "1 Test Road"));
        MenuCategory category = categoryRepository.saveAndFlush(new MenuCategory(restaurant, "Mains", 0));
        item = menuItemRepository.saveAndFlush(new MenuItem(category, "Paneer Bowl", "Rice and paneer",
                new BigDecimal("49.99"), null));
        owner = createUser("Owner", UserRole.CUSTOMER);
        otherCustomer = createUser("Other", UserRole.CUSTOMER);
        admin = createUser("Admin", UserRole.ADMIN);
    }

    @Test
    void customersListOnlyTheirOrdersAndCanViewTheirOwnDetails() throws Exception {
        UUID orderId = placeOrder(owner, "Owner Address");
        placeOrder(otherCustomer, "Other Address");

        mockMvc.perform(get("/api/orders/my?page=0&size=1")
                        .with(user(AuthenticatedUser.from(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].items[0].itemName").value("Paneer Bowl"));

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .with(user(AuthenticatedUser.from(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress").value("Owner Address"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(49.99));

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .with(user(AuthenticatedUser.from(otherCustomer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void adminCanViewPaginatedOrdersAndFilterByStatus() throws Exception {
        placeOrder(owner, "Owner Address");
        UUID otherOrder = placeOrder(otherCustomer, "Other Address");
        setStatus(otherOrder, OrderStatus.CONFIRMED);

        mockMvc.perform(get("/api/admin/orders?page=0&size=1")
                        .with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].customerName").value("Other"))
                .andExpect(jsonPath("$.content[0].deliveryAddress").value("Other Address"));

        mockMvc.perform(get("/api/admin/orders?status=CONFIRMED")
                        .with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(otherOrder.toString()));

        mockMvc.perform(get("/api/admin/orders?status=UNKNOWN")
                        .with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
        mockMvc.perform(get("/api/admin/orders?size=51")
                        .with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));

        mockMvc.perform(get("/api/admin/orders").with(user(AuthenticatedUser.from(owner))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/my").with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStatusWorkflowAllowsOnlySequentialProgressAndEarlyCancellation() throws Exception {
        UUID orderId = placeOrder(owner, "Owner Address");
        patchStatus(orderId, "CONFIRMED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        patchStatus(orderId, "PREPARING").andExpect(status().isOk());
        patchStatus(orderId, "COMPLETED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        patchStatus(orderId, "CANCELLED").andExpect(status().isConflict());
        patchStatus(orderId, "READY").andExpect(status().isOk());
        patchStatus(orderId, "COMPLETED").andExpect(status().isOk());
        patchStatus(orderId, "CONFIRMED").andExpect(status().isConflict());

        UUID cancellable = placeOrder(otherCustomer, "Other Address");
        patchStatus(cancellable, "CANCELLED").andExpect(status().isOk());
        assertThat(orderRepository.findById(cancellable).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        UUID confirmedCancellable = placeOrder(owner, "Owner Address 2");
        patchStatus(confirmedCancellable, "CONFIRMED").andExpect(status().isOk());
        patchStatus(confirmedCancellable, "CANCELLED").andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/orders/{orderId}/status", orderId)
                        .with(user(AuthenticatedUser.from(owner))).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadOrderDetailsButUnknownOrderReturnsNotFound() throws Exception {
        UUID orderId = placeOrder(owner, "Owner Address");
        mockMvc.perform(get("/api/orders/{orderId}", orderId).with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Owner"))
                .andExpect(jsonPath("$.contactPhone").value("+91 98765 43210"));
        mockMvc.perform(get("/api/orders/{orderId}", UUID.randomUUID())
                        .with(user(AuthenticatedUser.from(admin))))
                .andExpect(status().isNotFound());
    }

    private UserAccount createUser(String name, UserRole role) {
        return userAccountRepository.saveAndFlush(new UserAccount(name,
                name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", "test-hash", role));
    }

    private UUID placeOrder(UserAccount customer, String address) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "restaurantId", restaurant.getId(),
                "items", List.of(java.util.Map.of("menuItemId", item.getId(), "quantity", 1)),
                "contactName", customer.getName(),
                "contactPhone", "+91 98765 43210",
                "deliveryAddress", address));
        String response = mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.path("id").asText());
    }

    private void setStatus(UUID orderId, OrderStatus status) throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{orderId}/status", orderId)
                .with(user(AuthenticatedUser.from(admin))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("status", status.name()))));
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(UUID orderId, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/orders/{orderId}/status", orderId)
                .with(user(AuthenticatedUser.from(admin))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("status", status))));
    }
}
