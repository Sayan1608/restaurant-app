package com.restaurantapp.orders;

import com.restaurantapp.auth.AuthenticatedUser;
import com.restaurantapp.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/my")
    public OrderPageResponse<OrderResponse> myOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return orderService.myOrders(user, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(@PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.getOrder(orderId, user);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String rawIdempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        UUID idempotencyKey = parseIdempotencyKey(rawIdempotencyKey);
        OrderCreationResult result = orderService.create(user, idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + result.order().id()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.order());
    }

    private UUID parseIdempotencyKey(String key) {
        if (key == null || !UUID_PATTERN.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "A UUID Idempotency-Key header is required.");
        }
        return UUID.fromString(key);
    }
}
