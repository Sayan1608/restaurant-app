ALTER TABLE orders
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_orders_customer_idempotency_key
    ON orders (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
