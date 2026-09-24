# OrderFlow — Architecture

## Overview

OrderFlow is a **modular monolith**: one deployable Spring Boot application, internally
partitioned into modules (`auth`, `product`, `cart`, `order`, `inventory`, `payment`,
`shipment`, `notification`, `audit`, `outbox`, `kafka`, `idempotency`, `admin`) that
communicate only through well-defined service interfaces and events. Each module owns
its tables and could be extracted into a microservice without touching the others' code.

```
                     Client
                       │
                       ▼
              Spring Security (JWT filter chain, RBAC)
                       │
        ┌──────────────┼──────────────────┐
        ▼              ▼                  ▼
   Auth Module    Product Module     Order Module
        │              │                  │
        │         ┌────┴────┐      ┌──────┼──────────┐
        │         ▼         ▼      ▼      ▼          ▼
        │     Inventory  Cart   Payment Inventory  Shipment
        │              │          │      (saga)       │
        └──────────────┴──────────┴────────┬──────────┘
                                           ▼
                              Transactional Outbox (Postgres)
                                           │  (publisher)
                                           ▼
                                        Kafka topics
                                           │
                       ┌──────────┬────────┴────────┐
                       ▼          ▼                 ▼
                Notification  Analytics(DLT)    Audit / others
                                          
   Redis: product cache · idempotency replay · refresh tokens · (rate limits)
   Postgres: system of record (Flyway-managed schema)
```

## Order Saga (creation)

The order lifecycle spans inventory, payment and the order aggregate — deliberately
**not** one big ACID transaction. Instead a saga of independently committed phases:

```
POST /orders  (Idempotency-Key header)
   │
   ├─ idempotent replay? ──► return stored response
   ▼
[tx1] Create order (PENDING_PAYMENT)     -> durable even if later phases fail
   ▼
[tx2] Reserve inventory                  [own tx, optimistic lock + retry]
   │ failure ──► [tx] cancel order + order.cancelled event ──► stop
   ▼
[tx] record order.created via outbox
   ▼
[tx3] Charge payment (simulated gateway) [timeout retry, duplicate-safe]
   │ failure ──► payment.failed event ──► release inventory ──► [tx] CANCELLED
   ▼
[tx] PAID → CONFIRMED, payment.completed + order.confirmed via outbox
   ▼
[tx4] idempotency record + cart clear + audit entry
```

Because each phase commits, a payment decline leaves a durable CANCELLED order (with
cancel_reason), fully released stock, and `order.cancelled` flowing through Kafka —
the caller still receives a clean 402 PAYMENT_FAILED. Compensation is the saga's core
idea: a failed step triggers inverse operations of completed steps, and every
transition emits an event through the transactional outbox.

## Transactional Outbox

No module ever writes to Kafka inside a business transaction. Instead:

1. Business change + `outbox_events` insert commit **atomically** in Postgres.
2. A scheduled publisher (`OutboxPublisher`) claims pending rows with
   `SELECT ... FOR UPDATE SKIP LOCKED` (safe with multiple app instances),
   sends them to the mapped topic, marks them `PROCESSED`.
3. After 5 failed attempts a row is marked `FAILED` (visible for ops, never silently lost).

Result: no lost events on crash, no phantom events on rollback. Delivery to Kafka is
at-least-once; consumers deduplicate by `eventId`.

## Kafka event architecture

Topics: `order.events`, `payment.events`, `inventory.events`, `shipment.events` and
`order.events.dlt` (dead letter). Consumers run in the `orderflow-core` consumer group
with Spring Kafka's `DefaultErrorHandler`: 2 retries with 1s backoff, then the message
is published to the DLT with failure metadata. The order-event consumer is idempotent
(eventId dedupe) and fans notifications out from lifecycle events.

## Idempotency

`POST /orders` accepts an `Idempotency-Key` header. First request: business response is
stored in Postgres (`idempotency_keys`, unique on key+user+endpoint) and cached in Redis
(24h TTL). Replays return the stored response without executing the saga — duplicates can
never create two orders. Redis is an optimization; Postgres is the durable record, so a
Redis outage degrades to DB lookups, not to duplicate orders.

## Caching (cache-aside)

* `product::{id}` — 10 min TTL, invalidated on product update/delete.
* `productList::{queryHash}` — 5 min TTL, cleared on any product mutation.
* Read path: Redis hit → return; miss → Postgres → fill Redis. Cache is disabled
  automatically in test profiles and falls back to the DB when Redis is unreachable.

## Concurrency control

Inventory rows carry a JPA `@Version` (optimistic locking) plus DB CHECK constraints
(`available_quantity >= 0`). Reservations run in their own transaction with up to 3
optimistic-lock retries; exhaustion returns HTTP 409. The integration test
`InventoryConcurrencyIT` proves the invariant: 100 concurrent reservations against
stock = 5 produce exactly 5 successes and zero negative stock.

## Security

* JWT access tokens (15 min) + opaque single-use refresh tokens (7 d, Redis-stored, rotated on use).
* BCrypt password hashing; no plaintext secrets in the repo (`.env.example` only).
* RBAC at two layers: URL rules in `SecurityConfig` and `@PreAuthorize` on controllers.
* Rate limiting hooks (login/order-create/default per-minute limits) with 429 responses.
* Centralized error handler: standardized JSON errors, no stack traces or internals leaked.

## Observability

* Actuator exposes `health`, `info`, `metrics`, `prometheus`.
* Business counters: orders created/cancelled, saga compensations, payments
  completed/failed, inventory failures, outbox published/failed, emails sent.
* Prometheus scrapes `/actuator/prometheus`; the provisioned Grafana dashboard shows
  order rate, payment success/failure, latency (avg/p95), cache hit ratio and outbox flow.

## Failure scenarios demonstrated

| Scenario | Behavior |
|---|---|
| Duplicate order request | Idempotency replay, one order max |
| Concurrent reservations | Optimistic lock + retry; stock never negative |
| Payment decline | Saga compensation: release + cancel + events |
| Payment timeout | Bounded retries, then 504 + compensation |
| Kafka consumer failure | 2 retries → DLT |
| Redis down | DB fallback for idempotency; cache bypass |
