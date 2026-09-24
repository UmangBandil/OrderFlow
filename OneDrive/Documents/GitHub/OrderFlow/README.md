# OrderFlow

**Production-grade Order & Fulfillment Management Platform** — a modular-monolith
backend demonstrating the complete order lifecycle:

```
Customer → Cart → Order → Payment → Inventory Reservation → Fulfillment → Shipment → Delivery
```

Built to show real backend engineering: sagas with compensations, transactional outbox,
optimistic locking under concurrency, idempotent APIs, event-driven notifications,
cache-aside with Redis, JWT/RBAC security, and full observability.

---

## Highlights

| Concern | What's implemented |
|---|---|
| **Order saga** | Reserve → Pay → Confirm, with automatic compensation (release + cancel) on payment failure |
| **Transactional outbox** | DB transaction + event insert commit atomically; background relay to Kafka with `FOR UPDATE SKIP LOCKED` claiming |
| **Concurrency** | JPA `@Version` optimistic locking + bounded retries; DB CHECKs make negative stock impossible |
| **Idempotency** | `Idempotency-Key` on `POST /orders`; durable Postgres record + Redis fast path |
| **Payments** | Simulated gateway with deterministic decline/timeout hooks, retry on timeout, refunds |
| **Kafka** | 4 event topics + DLT, retry-with-backoff error handler, idempotent consumers |
| **Redis** | Product cache (cache-aside + invalidation), idempotency replay, refresh-token store |
| **Security** | JWT (short-lived access) + rotating refresh tokens, BCrypt, two-layer RBAC, rate-limit hooks |
| **Observability** | Micrometer business counters, Actuator, Prometheus, pre-provisioned Grafana dashboard |
| **Testing** | 55 unit tests + 15 Testcontainers integration tests (real PostgreSQL/Redis), incl. a 100-thread oversell test |

## Tech stack

Java 21 · Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator, Cache) ·
PostgreSQL 16 + Flyway · Redis 7 · Apache Kafka 3.8 (KRaft) · JJWT · springdoc-openapi ·
Micrometer/Prometheus/Grafana · Testcontainers · JUnit 5/Mockito/MockMvc ·
Docker & docker-compose · GitHub Actions (CI, integration, Docker) · Spotless/JaCoCo

## Quickstart

```bash
# 1. Configure
cp .env.example .env    # set JWT_SECRET before any real deployment

# 2. Run the whole stack (api + postgres + redis + kafka + prometheus + grafana)
docker compose -f docker/docker-compose.yml up --build

# 3. Explore
#    API        http://localhost:8080/api/v1/...
#    Swagger    http://localhost:8080/swagger-ui.html
#    Metrics    http://localhost:8080/actuator/prometheus
#    Prometheus http://localhost:9090
#    Grafana    http://localhost:3000  (admin/admin)
```

### Seeded demo logins (`SEED_DATA=true` profile `seed`)

| Role | Email | Password |
|---|---|---|
| ADMIN | admin@orderflow.io | Admin123! |
| CUSTOMER | customer@orderflow.io | Customer123! |
| WAREHOUSE_MANAGER | warehouse@orderflow.io | Warehouse123! |
| SUPPORT_AGENT | support@orderflow.io | Support123! |

### Try the end-to-end flow

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@orderflow.io","password":"Customer123!"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: demo-001' \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantity":2}],
       "shippingAddress":{"addressLine":"MG Road","city":"Pune","postalCode":"411001"}}' | jq
```

Send it twice with the same key — you get the same order back. Change a product's price
fraction to `.13` (e.g. `50.13`) to watch the saga compensate: payment declines,
inventory is released, the order is cancelled, and `order.cancelled` flows through Kafka.

## Testing

```bash
mvn test                 # 55 unit tests (no Docker needed)
mvn test -Dtest='*IT'    # 15 Testcontainers integration tests (Docker required)
mvn verify               # everything + coverage
```

Integration tests boot real PostgreSQL and Redis containers, run the Flyway migrations,
and exercise the full API — including `InventoryConcurrencyIT`: 100 concurrent
reservations against stock of 5 must yield exactly 5 successes and zero negative stock.

## Project layout

```
OrderFlow/
├── src/main/java/com/orderflow/
│   ├── auth/            # JWT, refresh tokens, users, admin user management
│   ├── product/         # catalog + categories, cache-aside
│   ├── cart/            # per-user cart
│   ├── order/           # order aggregate, saga, search, cancellation
│   ├── inventory/       # stock, optimistic locking, reservations
│   ├── payment/         # simulated gateway, refunds
│   ├── shipment/        # fulfillment status machine
│   ├── notification/    # email simulator (event-driven)
│   ├── audit/           # audit trail
│   ├── idempotency/     # durable idempotency records
│   ├── outbox/          # transactional outbox + relay
│   ├── kafka/           # listeners, retry + DLT
│   ├── seed/            # demo data (profile: seed)
│   └── common/          # security, errors, events, config
├── src/main/resources/db/migration/   # Flyway
├── src/test/            # unit + Testcontainers integration tests
├── docker/              # Dockerfile, docker-compose (Kafka in KRaft mode)
├── monitoring/          # Prometheus config, Grafana provisioning + dashboard
├── docs/                # architecture.md, database.md, events.md, api.md
└── .github/workflows/   # ci.yml, integration-tests.yml, docker.yml
```

## Documentation

* [Architecture](docs/architecture.md) — saga, outbox, caching, concurrency, failure scenarios
* [Database](docs/database.md) — ER overview, tables, indexes, migration policy
* [Events](docs/events.md) — event catalog, envelope, topic routing, retry/DLT
* [API](docs/api.md) — endpoint reference, error codes, auth matrix

## CI/CD

* **ci.yml** — compile + unit tests + Spotless check, JaCoCo artifact
* **integration-tests.yml** — Testcontainers suite on every push/PR
* **docker.yml** — multi-stage image build to GHCR (buildpack-free, non-root runtime)
