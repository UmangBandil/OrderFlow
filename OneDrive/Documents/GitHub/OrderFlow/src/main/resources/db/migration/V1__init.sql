-- =====================================================================
-- OrderFlow schema (Flyway V1)
-- =====================================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN', 'WAREHOUSE_MANAGER', 'SUPPORT_AGENT'))
);

CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    category_id BIGINT,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE TABLE warehouses (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    city    VARCHAR(255) NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT  NOT NULL,
    warehouse_id       BIGINT  NOT NULL,
    available_quantity INT     NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
    reserved_quantity  INT     NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
);

CREATE TABLE carts (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL CHECK (quantity > 0),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE orders (
    id                 BIGSERIAL PRIMARY KEY,
    order_number       VARCHAR(32)  NOT NULL,
    user_id            BIGINT       NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    total_amount       NUMERIC(12,2) NOT NULL,
    shipping_address   VARCHAR(500) NOT NULL,
    cancel_reason      VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'CREATED', 'PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PROCESSING',
        'SHIPPED', 'DELIVERED', 'PAYMENT_FAILED', 'CANCELLED', 'REFUNDED'))
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    provider_ref VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider_ref),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
);

CREATE TABLE shipments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    warehouse_id    BIGINT,
    carrier         VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    shipped_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_shipments_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_shipments_status CHECK (status IN (
        'CREATED', 'PACKED', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'))
);

CREATE TABLE refunds (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   BIGINT NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    reason       VARCHAR(500),
    status       VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

CREATE TABLE idempotency_keys (
    id            BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id       BIGINT       NOT NULL,
    endpoint      VARCHAR(255) NOT NULL,
    response_body TEXT        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_keys_key_user_endpoint UNIQUE (idempotency_key, user_id, endpoint)
);
CREATE INDEX idx_idempotency_keys_user_id ON idempotency_keys (user_id);

CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID         NOT NULL,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        TEXT         NOT NULL,
    trace_id       VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);
CREATE INDEX idx_outbox_events_status_id ON outbox_events (status, id);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- ---- Order query indexes (search filters) ----
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_shipments_order_id ON shipments (order_id);
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);
CREATE INDEX idx_products_category_status ON products (category_id, status);
