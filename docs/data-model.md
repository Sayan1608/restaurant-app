# Initial data model

The MVP uses UUID primary keys and PostgreSQL `TIMESTAMPTZ` timestamps stored in UTC. All prices and totals use `NUMERIC(12, 2)` / Java `BigDecimal`, in INR. The checkout flow has no real payment integration yet, so a `payments` table is deferred until a payment method needs persistent payment state.

```mermaid
erDiagram
    USERS ||--o{ ORDERS : places
    RESTAURANTS ||--o{ MENU_CATEGORIES : has
    MENU_CATEGORIES ||--o{ MENU_ITEMS : contains
    RESTAURANTS ||--o{ ORDERS : receives
    ORDERS ||--|{ ORDER_ITEMS : contains
    MENU_ITEMS o|--o{ ORDER_ITEMS : references

    USERS {
        uuid id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar role
        timestamptz created_at
    }
    RESTAURANTS {
        uuid id PK
        varchar name UK
        text description
        text address
        boolean active
        timestamptz created_at
    }
    MENU_CATEGORIES {
        uuid id PK
        uuid restaurant_id FK
        varchar name
        integer display_order
        boolean active
    }
    MENU_ITEMS {
        uuid id PK
        uuid category_id FK
        varchar name
        numeric price
        boolean available
        timestamptz created_at
        timestamptz updated_at
    }
    ORDERS {
        uuid id PK
        uuid customer_id FK
        uuid restaurant_id FK
        varchar status
        numeric subtotal
        numeric delivery_fee
        numeric tax
        numeric total
        text delivery_address
        timestamptz created_at
        timestamptz updated_at
    }
    ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        uuid menu_item_id FK "nullable after menu item removal"
        varchar item_name_snapshot
        numeric unit_price_snapshot
        integer quantity
        numeric line_total
    }
```

Order items retain the purchased name and unit price. The menu-item foreign key uses `ON DELETE SET NULL`, while order rows and restaurants are restricted from deletion where they would erase order history. Application code should deactivate restaurants, categories, and items instead of deleting them during normal operation.

Local sample restaurant data is inserted only when the backend runs with the explicit `development` Spring profile. It creates no user accounts or credentials.
