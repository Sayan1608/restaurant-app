CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE restaurants (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    address TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_restaurants_name UNIQUE (name)
);

CREATE TABLE menu_categories (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_menu_categories_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_menu_categories_restaurant_name UNIQUE (restaurant_id, name),
    CONSTRAINT ck_menu_categories_display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_menu_categories_restaurant_display_order
    ON menu_categories (restaurant_id, display_order, id);

CREATE TABLE menu_items (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    image_url VARCHAR(2048),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_menu_items_category
        FOREIGN KEY (category_id) REFERENCES menu_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_menu_items_price_positive CHECK (price > 0)
);

CREATE INDEX ix_menu_items_category_available
    ON menu_items (category_id, available, name);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total NUMERIC(12, 2) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    delivery_address TEXT NOT NULL,
    delivery_notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_orders_status CHECK (status IN ('PLACED', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_orders_amounts CHECK (
        subtotal > 0 AND delivery_fee >= 0 AND tax >= 0 AND total > 0
    ),
    CONSTRAINT ck_orders_total_matches_components CHECK (total = subtotal + delivery_fee + tax)
);

CREATE INDEX ix_orders_customer_created_at
    ON orders (customer_id, created_at DESC, id);

CREATE INDEX ix_orders_status_created_at
    ON orders (status, created_at, id);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    menu_item_id UUID,
    item_name_snapshot VARCHAR(160) NOT NULL,
    unit_price_snapshot NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_menu_item
        FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_items_unit_price_positive CHECK (unit_price_snapshot > 0),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_line_total_positive CHECK (line_total > 0),
    CONSTRAINT ck_order_items_total_matches_quantity CHECK (line_total = unit_price_snapshot * quantity)
);

CREATE INDEX ix_order_items_order ON order_items (order_id, id);
