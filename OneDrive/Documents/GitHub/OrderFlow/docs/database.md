# OrderFlow — Database Design

PostgreSQL is the system of record. The schema is fully managed by Flyway
(`src/main/resources/db/migration/V1__init.sql`).

## Entity-relationship overview

```
users ─┬─< orders ─┬─< order_items >─ products >─ categories
       │           ├─< payments ─< refunds
       │           ├─< shipments
       │           └─< outbox_events (by aggregate)
       ├─< carts ─< cart_items >─ products
       └─< audit_logs

warehouses ─< inventory >─ products        (unique: product+warehouse)
idempotency_keys (unique: key+user+endpoint)
```

## Tables

| Table | Purpose | Notable constraints |
|---|---|---|
| `users` | Accounts + roles | unique email; role CHECK |
| `categories` | Product taxonomy | unique name |
| `products` | Catalog items | unique SKU; price >= 0; status CHECK; `version` for optimistic locking |
| `warehouses` | Fulfillment sites | active flag |
| `inventory` | Stock per product+warehouse | unique(product, warehouse); `available_quantity >= 0`; `reserved_quantity >= 0`; `version` |
| `carts` / `cart_items` | Per-user cart | unique cart per user; quantity > 0; cascade delete |
| `orders` | Order aggregate root | unique order number; status CHECK (10 states); `version` |
| `order_items` | Line items with unit price snapshot | quantity > 0; cascade delete |
| `payments` | One per order (provider-ref keyed) | unique provider ref; status CHECK |
| `refunds` | Refund records per payment | status CHECK |
| `shipments` | Fulfillment tracking | unique tracking number; status CHECK; warehouse id |
| `audit_logs` | Who did what, old/new values | indexed user, timestamp, entity |
| `idempotency_keys` | Stored responses for replay | unique(key, user, endpoint) |
| `outbox_events` | Transactional outbox | unique event id; status CHECK; indexed (status, id) |

## Indexes for query paths

Order search (`GET /orders` with filters) is served by:

```
idx_orders_user_id     (user_id)
idx_orders_status      (status)
idx_orders_created_at  (created_at)     -- date-range filters
idx_order_items_product_id
idx_payments_order_id / idx_shipments_order_id
idx_products_category_status            -- catalog filters
```

The outbox claim query uses `(status, id)` with `FOR UPDATE SKIP LOCKED`, giving an
efficient, contention-free batch claim across publisher instances.

## Money & time

* Money: `NUMERIC(12,2)` everywhere; mapped to `BigDecimal` with `equalsByComparingTo`
  in tests (never double/float).
* Time: `TIMESTAMPTZ` with Hibernate UTC JDBC time zone.

## Migrations

* `V1__init.sql` — full initial schema.
* Policy: every schema change ships as a new `V<N>__desc.sql`; no `ddl-auto` mutations
  in production (`validate` only).
