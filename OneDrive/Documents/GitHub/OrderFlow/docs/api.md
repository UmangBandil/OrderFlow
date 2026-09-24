# OrderFlow — API Reference (v1)

Base URL: `http://localhost:8080/api/v1` · Interactive docs: `/swagger-ui.html` ·
OpenAPI JSON: `/v3/api-docs`

All error responses share one shape:

```json
{
  "timestamp": "2026-09-25T10:30:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "Validation failed",
  "path": "/api/v1/orders",
  "fieldErrors": [{"field": "email", "message": "must be a well-formed email"}]
}
```

Error codes: `RESOURCE_NOT_FOUND` (404), `INVALID_REQUEST` (400), `UNAUTHORIZED` (401),
`FORBIDDEN` (403), `CONFLICT` (409), `INSUFFICIENT_INVENTORY` (409), `PAYMENT_FAILED`
(402), `ORDER_NOT_CANCELLABLE` (409), `DUPLICATE_REQUEST` (409),
`RATE_LIMIT_EXCEEDED` (429), `SIMULATED_PAYMENT_TIMEOUT` (504).

## Authentication `/auth`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | – | `{email, password (8+), fullName}` → 201, token pair |
| POST | `/auth/login` | – | → `{accessToken, refreshToken, email, role}` |
| POST | `/auth/refresh` | – | single-use refresh token rotation |
| POST | `/auth/logout` | – | revokes the refresh token |

## Products `/products`

| Method | Path | Auth |
|---|---|---|
| GET | `/products?page&size&sort&status&category&search` | public |
| GET | `/products/{id}` | public |
| POST | `/products` | ADMIN |
| PUT | `/products/{id}` | ADMIN |
| DELETE | `/products/{id}` | ADMIN (soft: DISCONTINUED) |

Product cache: `product::{id}` (10 min TTL), invalidated on mutation; list cache cleared
on any product change.

## Cart `/cart`

| Method | Path | Auth |
|---|---|---|
| GET | `/cart` | CUSTOMER+ |
| POST | `/cart/items` | `{productId, quantity}` |
| PUT | `/cart/items/{itemId}` | `{quantity}` |
| DELETE | `/cart/items/{itemId}` | |
| DELETE | `/cart` | clear cart |

## Orders `/orders`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/orders` | CUSTOMER+ | **Idempotency-Key** header supported; body: `{items:[{productId,quantity}], shippingAddress:{addressLine,city,postalCode,country?}}` — items fall back to the server cart when omitted |
| GET | `/orders?status&customer&from&to&paymentStatus&warehouse` | CUSTOMER (own) / staff (all) | paginated |
| GET | `/orders/{id}` | owner or staff | |
| POST | `/orders/{id}/cancel` | owner (eligible states) or staff | releases inventory |

Order creation runs the saga: reserve → pay → confirm; failures compensate
automatically.

## Payments `/payments`

| Method | Path | Auth |
|---|---|---|
| POST | `/payments` | `{orderId, amount}` — duplicate-safe via provider ref |
| GET | `/payments/{id}` | |
| POST | `/payments/{id}/refund` | `{amount, reason}` — SUCCESS payments only |

Simulated gateway: totals whose cents end in **.13** always decline; **.14** always
time out (then the service retries twice). Otherwise governed by
`PAYMENT_SIMULATION_FAILURE_RATE` / `PAYMENT_SIMULATION_TIMEOUT_RATE`.

## Inventory `/inventory` — ADMIN, WAREHOUSE_MANAGER

| Method | Path | Notes |
|---|---|---|
| GET | `/inventory` | all rows |
| GET | `/inventory/{productId}` | one row |
| POST | `/inventory/reserve` | `{productId, quantity}` |
| POST | `/inventory/release` | `{productId, quantity}` |

Optimistic locking + retry; stock can never go negative (enforced in the domain and by
DB CHECK constraints).

## Shipments `/shipments` — ADMIN, WAREHOUSE_MANAGER

| Method | Path | Notes |
|---|---|---|
| POST | `/shipments` | `{orderId, carrier}` — order must be CONFIRMED/PAID |
| GET | `/shipments/{id}` | |
| GET | `/shipments/order/{orderId}` | |
| PUT | `/shipments/{id}/status` | `{status}` — validated transitions; DELIVERED completes the order |

## Admin `/admin` — ADMIN

| Method | Path |
|---|---|
| GET | `/admin/users?page&size` |
| PUT | `/admin/users/{id}/role` |
| GET | `/admin/audit-logs?page&size` |
| GET | `/admin/metrics` — custom counter names |

## Observability

* `GET /actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`.
* Swagger groups every module with request/response schemas and auth requirements.
