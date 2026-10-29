package com.restaurantapp.orders;

import com.restaurantapp.auth.AuthenticatedUser;
import com.restaurantapp.common.ApiException;
import com.restaurantapp.menu.MenuItem;
import com.restaurantapp.menu.MenuItemRepository;
import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.restaurant.RestaurantService;
import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import com.restaurantapp.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final UserAccountRepository userAccountRepository;
    private final RestaurantService restaurantService;
    private final MenuItemRepository menuItemRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(UserAccountRepository userAccountRepository, RestaurantService restaurantService,
            MenuItemRepository menuItemRepository, CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository) {
        this.userAccountRepository = userAccountRepository;
        this.restaurantService = restaurantService;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public OrderCreationResult create(AuthenticatedUser authenticatedUser, UUID idempotencyKey,
            CreateOrderRequest request) {
        rejectDuplicateLines(request.items());
        String requestFingerprint = fingerprint(request);
        UserAccount customer = userAccountRepository.findByIdForCheckout(authenticatedUser.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Authentication is required."));

        var previousOrder = orderRepository.findByCustomer_IdAndIdempotencyKey(customer.getId(), idempotencyKey);
        if (previousOrder.isPresent()) {
            CustomerOrder existing = previousOrder.get();
            if (!existing.getRequestFingerprint().equals(requestFingerprint)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This checkout key was already used for a different request.");
            }
            return new OrderCreationResult(OrderResponse.from(existing,
                    orderItemRepository.findAllByOrder_IdOrderById(existing.getId())), true);
        }

        Restaurant restaurant = restaurantService.findActive(request.restaurantId());
        List<UUID> requestedIds = request.items().stream().map(OrderLineRequest::menuItemId).toList();
        Map<UUID, MenuItem> currentItems = menuItemRepository
                .findAvailableForCheckout(restaurant.getId(), requestedIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, item -> item));
        if (currentItems.size() != requestedIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "MENU_ITEM_UNAVAILABLE",
                    "One or more items are no longer available. Refresh the menu and try again.");
        }

        List<PricedLine> pricedLines = new ArrayList<>(request.items().size());
        BigDecimal subtotal = ZERO_MONEY;
        for (OrderLineRequest line : request.items()) {
            MenuItem item = currentItems.get(line.menuItemId());
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            pricedLines.add(new PricedLine(item, line.quantity()));
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal total = subtotal.add(ZERO_MONEY).add(ZERO_MONEY);
        CustomerOrder order = new CustomerOrder(customer, restaurant, OrderStatus.PLACED,
                subtotal, ZERO_MONEY, ZERO_MONEY, total,
                request.contactName().trim(), request.contactPhone().trim(), request.deliveryAddress().trim(),
                cleanOptional(request.deliveryNotes()), idempotencyKey, requestFingerprint);
        orderRepository.save(order);

        List<OrderItem> orderItems = pricedLines.stream()
                .map(line -> new OrderItem(order, line.item(), line.item().getName(), line.item().getPrice(), line.quantity()))
                .toList();
        orderItemRepository.saveAll(orderItems);
        return new OrderCreationResult(OrderResponse.from(order, orderItems), false);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse<OrderResponse> myOrders(AuthenticatedUser customer, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<CustomerOrder> orders = orderRepository.findAllByCustomer_Id(customer.getId(), pageable);
        return OrderPageResponse.from(orders, summarize(orders.getContent()));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(UUID orderId, AuthenticatedUser requester) {
        CustomerOrder order = orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> orderNotFound());
        if (requester.getRole() == UserRole.CUSTOMER && !order.getCustomer().getId().equals(requester.getId())) {
            throw orderNotFound();
        }
        return OrderDetailResponse.from(order, orderItemRepository.findAllByOrder_IdOrderById(orderId));
    }

    @Transactional(readOnly = true)
    public OrderPageResponse<OrderDetailResponse> adminOrders(int page, int size, OrderStatus status) {
        Pageable pageable = pageRequest(page, size);
        Page<CustomerOrder> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAllByStatus(status, pageable);
        List<CustomerOrder> content = orders.getContent();
        Map<UUID, List<OrderItem>> itemsByOrder = itemsByOrder(content);
        List<OrderDetailResponse> details = content.stream()
                .map(order -> OrderDetailResponse.from(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return OrderPageResponse.from(orders, details);
    }

    @Transactional
    public OrderDetailResponse updateStatus(UUID orderId, OrderStatus requestedStatus) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> orderNotFound());
        if (!isAllowedTransition(order.getStatus(), requestedStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION",
                    "The order cannot move from " + order.getStatus() + " to " + requestedStatus + ".");
        }
        order.changeStatus(requestedStatus);
        return OrderDetailResponse.from(order, orderItemRepository.findAllByOrder_IdOrderById(orderId));
    }

    private List<OrderResponse> summarize(List<CustomerOrder> orders) {
        Map<UUID, List<OrderItem>> itemsByOrder = itemsByOrder(orders);
        return orders.stream().map(order -> OrderResponse.from(order,
                itemsByOrder.getOrDefault(order.getId(), List.of()))).toList();
    }

    private Map<UUID, List<OrderItem>> itemsByOrder(List<CustomerOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        List<UUID> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        Map<UUID, List<OrderItem>> grouped = new HashMap<>();
        orderItemRepository.findAllForOrders(orderIds).forEach(item ->
                grouped.computeIfAbsent(item.getOrder().getId(), ignored -> new ArrayList<>()).add(item));
        return grouped;
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST",
                    "Page must be non-negative and size must be between 1 and 50.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "The order was not found.");
    }

    private boolean isAllowedTransition(OrderStatus current, OrderStatus requested) {
        if (requested == null) return false;
        return switch (current) {
            case PLACED -> requested == OrderStatus.CONFIRMED || requested == OrderStatus.CANCELLED;
            case CONFIRMED -> requested == OrderStatus.PREPARING || requested == OrderStatus.CANCELLED;
            case PREPARING -> requested == OrderStatus.READY;
            case READY -> requested == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private void rejectDuplicateLines(List<OrderLineRequest> lines) {
        Set<UUID> itemIds = new HashSet<>();
        if (lines.stream().anyMatch(line -> !itemIds.add(line.menuItemId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_LINES",
                    "Submit each menu item once with its desired quantity.");
        }
    }

    private String fingerprint(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, request.restaurantId().toString());
        request.items().stream().sorted(Comparator.comparing(line -> line.menuItemId().toString()))
                .forEach(line -> {
                    append(canonical, line.menuItemId().toString());
                    append(canonical, line.quantity().toString());
                });
        append(canonical, request.contactName().trim());
        append(canonical, request.contactPhone().trim());
        append(canonical, request.deliveryAddress().trim());
        append(canonical, cleanOptional(request.deliveryNotes()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void append(StringBuilder target, String value) {
        String safeValue = value == null ? "" : value;
        target.append(safeValue.length()).append(':').append(safeValue);
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record PricedLine(MenuItem item, int quantity) {
    }
}
