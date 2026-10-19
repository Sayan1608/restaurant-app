package com.restaurantapp.orders;

import com.restaurantapp.restaurant.Restaurant;
import com.restaurantapp.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserAccount customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "text")
    private String deliveryAddress;

    @Column(name = "delivery_notes", length = 500)
    private String deliveryNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerOrder() {
    }

    public CustomerOrder(UserAccount customer, Restaurant restaurant, OrderStatus status,
            BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal tax, BigDecimal total,
            String contactName, String contactPhone, String deliveryAddress, String deliveryNotes) {
        this.customer = Objects.requireNonNull(customer, "customer is required");
        this.restaurant = Objects.requireNonNull(restaurant, "restaurant is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal is required");
        this.deliveryFee = Objects.requireNonNull(deliveryFee, "delivery fee is required");
        this.tax = Objects.requireNonNull(tax, "tax is required");
        this.total = Objects.requireNonNull(total, "total is required");
        if (subtotal.signum() <= 0 || deliveryFee.signum() < 0 || tax.signum() < 0 || total.signum() <= 0
                || total.compareTo(subtotal.add(deliveryFee).add(tax)) != 0) {
            throw new IllegalArgumentException("order amounts are invalid");
        }
        this.contactName = Objects.requireNonNull(contactName, "contact name is required").trim();
        this.contactPhone = Objects.requireNonNull(contactPhone, "contact phone is required").trim();
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress, "delivery address is required").trim();
        this.deliveryNotes = deliveryNotes;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getCustomer() {
        return customer;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
