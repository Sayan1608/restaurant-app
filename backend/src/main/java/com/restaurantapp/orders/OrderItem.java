package com.restaurantapp.orders;

import com.restaurantapp.menu.MenuItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @Column(name = "item_name_snapshot", nullable = false, length = 160)
    private String itemNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public OrderItem(CustomerOrder order, MenuItem menuItem, String itemNameSnapshot,
            BigDecimal unitPriceSnapshot, int quantity) {
        this.order = Objects.requireNonNull(order, "order is required");
        this.menuItem = menuItem;
        this.itemNameSnapshot = Objects.requireNonNull(itemNameSnapshot, "item name snapshot is required").trim();
        this.unitPriceSnapshot = Objects.requireNonNull(unitPriceSnapshot, "unit price snapshot is required");
        if (unitPriceSnapshot.signum() <= 0 || unitPriceSnapshot.scale() > 2 || quantity <= 0) {
            throw new IllegalArgumentException("order item price and quantity are invalid");
        }
        this.quantity = quantity;
        this.lineTotal = unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
