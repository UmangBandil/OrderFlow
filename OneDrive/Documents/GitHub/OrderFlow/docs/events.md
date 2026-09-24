# OrderFlow — Events

All domain events flow through the **transactional outbox** (`outbox_events`) and are
relayed to Kafka by `OutboxPublisher`. Delivery is at-least-once; consumers deduplicate
by `eventId`.

## Envelope

```json
{
  "eventId":   "c0a80101-7f2a-4c1e-9f3e-0a1b2c3d4e5f",
  "eventType": "order.confirmed",
  "aggregateType": "order",
  "aggregateId": "ORD-20260925-8F3A21BC",
  "payload":   { "orderId": 42 },
  "traceId":   null,
  "occurredAt": "2026-09-25T10:30:00Z"
}
```

The Kafka message key is the aggregate id, so all events for one order land on the same
partition in order.

## Catalog

| Event type | Emitted when | Payload highlights |
|---|---|---|
| `order.created` | Order row persisted | orderId, total |
| `order.confirmed` | Payment succeeded | orderId |
| `order.cancelled` | Saga compensation, inventory failure, or user cancellation | reason |
| `payment.completed` | Gateway approved the charge | orderId |
| `payment.failed` | Gateway declined / timeout exhausted | reason |
| `refund.created` | Successful refund | paymentId, amount, reason |
| `inventory.reserved` | Stock reserved for an order | productId, quantity |
| `inventory.released` | Compensation/stock returned | productId, quantity |
| `shipment.created` | Warehouse creates a shipment | trackingNumber, carrier |
| `shipment.status_changed` | Shipment status transition | from, to |
| `shipment.delivered` | Shipment DELIVERED | orderId |
| `product.deleted` | Product discontinued | productId |

## Topic routing

| Topic | Events |
|---|---|
| `order.events` | order.*, product.deleted (default) |
| `payment.events` | payment.*, refund.created |
| `inventory.events` | inventory.* |
| `shipment.events` | shipment.* |
| `order.events.dlt` | failed order-event messages after retries |

## Consumers

* Group id `orderflow-core`; concurrency 3.
* `OrderEventConsumer` fans lifecycle events out to the notification simulator and
  demonstrates idempotent consumption (eventId dedupe window).
* Failure policy: `DefaultErrorHandler` — 2 retries with 1 s backoff, then publish to
  the DLT (`<topic>.dlt`) with headers: original topic/partition/offset, exception
  class + message.

## Notification mapping (email simulator)

| Trigger | Template |
|---|---|
| order.confirmed | ORDER_CONFIRMED |
| payment.failed | PAYMENT_FAILED |
| order.cancelled | ORDER_CANCELLED |

## Adding a new event

1. Publish from the owning service via `EventPublisher.publish(type, aggregateType, id, payload)` inside the business transaction.
2. Route it in `OutboxPublisher.resolveTopic` (or let it default to `order.events`).
3. Document it here and add a consumer test if it has side effects.
