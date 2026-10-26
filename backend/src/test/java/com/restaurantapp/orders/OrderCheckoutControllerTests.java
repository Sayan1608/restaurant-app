package com.restaurantapp.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantapp.auth.AuthenticatedUser;
import com.restaurantapp.common.ApiException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderCheckoutControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuCategoryRepository categoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private CustomerOrderRepository customerOrderRepository;
    @MockitoSpyBean private OrderItemRepository orderItemRepository;

    private Restaurant restaurant;
    private MenuItem item;
    private UserAccount customer;

    @BeforeEach
    void setUp() {
        restaurant = restaurantRepository.saveAndFlush(new Restaurant("Checkout " + UUID.randomUUID(), null, "1 Test Road"));
        MenuCategory category = categoryRepository.saveAndFlush(new MenuCategory(restaurant, "Mains", 0));
        item = menuItemRepository.saveAndFlush(new MenuItem(category, "Paneer Bowl", "Rice and paneer",
                new BigDecimal("49.99"), null));
        customer = userAccountRepository.saveAndFlush(new UserAccount("Test Customer",
                "checkout-" + UUID.randomUUID() + "@example.test", "test-hash", UserRole.CUSTOMER));
    }

    @Test
    void createsAnOrderUsingCurrentServerPriceAndPersistsItemSnapshot() throws Exception {
        UUID key = UUID.randomUUID();
        String body = checkoutBody(3);

        String response = mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items[0].itemName").value("Paneer Bowl"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(49.99))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].lineTotal").value(149.97))
                .andExpect(jsonPath("$.subtotal").value(149.97))
                .andExpect(jsonPath("$.deliveryFee").value(0))
                .andExpect(jsonPath("$.tax").value(0))
                .andExpect(jsonPath("$.total").value(149.97))
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        UUID orderId = UUID.fromString(result.path("id").asText());
        assertThat(customerOrderRepository.findById(orderId)).isPresent();
        assertThat(orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getOrder().getId().equals(orderId)).toList()).singleElement()
                .satisfies(orderItem -> {
                    assertThat(orderItem.getItemNameSnapshot()).isEqualTo("Paneer Bowl");
                    assertThat(orderItem.getUnitPriceSnapshot()).isEqualByComparingTo("49.99");
                    assertThat(orderItem.getLineTotal()).isEqualByComparingTo("149.97");
                });

        item.update(item.getCategory(), item.getName(), item.getDescription(), new BigDecimal("79.00"), null);
        menuItemRepository.flush();
        assertThat(orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getOrder().getId().equals(orderId)).findFirst().orElseThrow()
                .getUnitPriceSnapshot())
                .isEqualByComparingTo("49.99");
    }

    @Test
    void rejectsUnavailableMenuItemsWithoutCreatingAnOrder() throws Exception {
        item.setAvailable(false);
        menuItemRepository.flush();

        mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_ITEM_UNAVAILABLE"));
        assertThat(customerOrderRepository.count()).isZero();
    }

    @Test
    void validatesQuantityAndRejectsDuplicateItemLines() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists());

        Map<String, Object> duplicate = checkoutRequest(1);
        duplicate.put("items", List.of(Map.of("menuItemId", item.getId(), "quantity", 1),
                Map.of("menuItemId", item.getId(), "quantity", 2)));
        mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_LINES"));
        assertThat(customerOrderRepository.count()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    void rollsBackTheOrderWhenSavingItsItemsFails() throws Exception {
        doThrow(new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ITEM_WRITE_FAILED",
                "Could not persist order items."))
                .when(orderItemRepository).saveAll(any(Iterable.class));

        mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_WRITE_FAILED"));

        assertThat(customerOrderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
    }

    @Test
    void sameIdempotencyKeyReplaysTheExistingOrderButRejectsChangedPayload() throws Exception {
        UUID key = UUID.randomUUID();
        String body = checkoutBody(2);
        String first = mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).path("id").asText())
                .isEqualTo(objectMapper.readTree(first).path("id").asText());
        assertThat(customerOrderRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/orders")
                        .with(user(AuthenticatedUser.from(customer))).with(csrf().asHeader())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(checkoutBody(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(customerOrderRepository.count()).isEqualTo(1);
    }

    @Test
    void requiresCustomerAuthenticationAndCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/orders").with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(1)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders")
                        .with(user("staff").roles("ADMIN")).with(csrf().asHeader())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(1)))
                .andExpect(status().isForbidden());
    }

    private String checkoutBody(int quantity) throws Exception {
        return objectMapper.writeValueAsString(checkoutRequest(quantity));
    }

    private Map<String, Object> checkoutRequest(int quantity) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("restaurantId", restaurant.getId());
        request.put("items", List.of(Map.of("menuItemId", item.getId(), "quantity", quantity,
                "unitPrice", 0.01, "lineTotal", 0.01)));
        request.put("contactName", "Test Customer");
        request.put("contactPhone", "+91 98765 43210");
        request.put("deliveryAddress", "22 Test Street, Bengaluru");
        request.put("deliveryNotes", null);
        request.put("total", 0.01);
        return request;
    }
}
