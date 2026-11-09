# Order Processing & Customer Integration API
### MuleSoft 4 Interview Demonstration — Original Application

---

## Overview

A standalone, original MuleSoft 4 REST API for an **Order Processing System** that demonstrates every concept required by the interview specification:

| Requirement | Implementation |
|---|---|
| **Java Component** | `OrderValidator.validate()` — business rule validation |
| **Java Transformer** | `OrderUtils.buildAuditEntry()`, `OrderUtils.calculateOrderMetrics()` |
| **Java Filter** | `OrderValidator.isValidStatusTransition()` — state-machine guard |
| **Error Handling** | Global handler (5 error types) + flow-level try/catch in Scatter-Gather |
| **Process Orchestration** | Scatter-Gather, Choice Router, Async VM Queue, Flow-ref sub-flows |
| **Data Transformation** | 8 DataWeave 2.0 transformations across all flows |
| **Testing** | 13 MUnit tests (flow tests + Java unit tests) |
| **Cloud Connector** | HTTP Connector — REST API + JSONPlaceholder external CRM |
| **Transport/Module** | VM Connector (async messaging), Java Module |
| **Originality** | Custom state machine, parallel enrichment, priority routing |

---

## Architecture

```
EXPERIENCE LAYER (HTTP REST API — port 8081)
  GET    /api/v1/health
  POST   /api/v1/orders          ← Full 8-step orchestration
  GET    /api/v1/orders
  GET    /api/v1/orders/{id}
  PUT    /api/v1/orders/{id}
  DELETE /api/v1/orders/{id}
  GET    /api/v1/orders/stats

PROCESS LAYER
  create-order-flow:
    Step 1: Java — OrderValidator.validate()         [Business rules]
    Step 2: Java — OrderValidator.generateOrderId()  [Unique ID]
    Step 3: Scatter-Gather (parallel):
              Route A: customer-enrichment-subflow   [External REST API]
              Route B: OrderUtils.buildAuditEntry()  [Java Transformer]
    Step 4: DataWeave — build order domain object    [Data Transform]
    Step 5: Choice Router — PRIORITY/EXPRESS/STANDARD[Orchestration]
    Step 6: Java — OrderRepository.save()            [Persistence]
    Step 7: Async vm:publish (fire-and-forget)        [Async Messaging]
    Step 8: DataWeave — 201 Created response         [Data Transform]

  Async notification consumer (vm:listener):
    - Event-driven (not scheduler/polling)
    - redelivery-policy (3 retries per message)
    - MULE:REDELIVERY_EXHAUSTED → Dead Letter Queue

SYSTEM LAYER
  OrderRepository     — ConcurrentHashMap (in-memory, thread-safe)
  JSONPlaceholder API — jsonplaceholder.typicode.com/users/{id}
```

### Order Priority Rules
```
totalAmount < $1,000    → STANDARD  → STANDARD_LANE
totalAmount $1,000-$9,999 → EXPRESS → EXPRESS_LANE
totalAmount >= $10,000  → PRIORITY  → PRIORITY_LANE
```

### Order Status State Machine
```
PENDING → CONFIRMED | CANCELLED
CONFIRMED → PROCESSING | CANCELLED
PROCESSING → SHIPPED | CANCELLED
SHIPPED → DELIVERED
DELIVERED → (terminal)
CANCELLED → (terminal)
```

---

## Project Structure

```
order-processing-api/
├── pom.xml                                    Mule 4.6.2, Java 11, MUnit
├── mule-artifact.json                         Runtime config
├── README.md
├── src/main/
│   ├── java/com/mulesoft/interview/
│   │   ├── OrderValidator.java    Java Component + Filter (validation, state machine)
│   │   ├── OrderUtils.java        Java Transformer (audit, metrics, PII masking)
│   │   └── OrderRepository.java   Thread-safe in-memory data store
│   ├── mule/
│   │   ├── global-config.xml      HTTP, VM, global error handler
│   │   ├── order-api.xml          Flows 1-4: health, create, list, get
│   │   └── order-subflows.xml     Flows 5-8: update, delete, stats, enrichment, consumer
│   └── resources/
│       ├── application.yaml       Property placeholders
│       └── log4j2.xml             Logging config
└── src/test/munit/
    └── order-api-test-suite.xml   13 MUnit tests
```

---

## How to Run

### Prerequisites
- Anypoint Code Builder (VS Code extension) or Anypoint Studio 7.x
- Java JDK 11+
- Anypoint Platform credentials (for Maven dependency download)

### Run in Anypoint Code Builder
1. Open `c:\Users\smend\projects\order-processing-api` in VS Code
2. ACB auto-detects the Mule project
3. Press **F5** or click Run → Run Mule Application
4. API available at `http://localhost:8081`

### Maven settings.xml (required for dependencies)
```xml
<settings>
  <servers>
    <server>
      <id>anypoint-exchange-v3</id>
      <username>YOUR_ANYPOINT_USERNAME</username>
      <password>YOUR_ANYPOINT_PASSWORD</password>
    </server>
    <server>
      <id>mulesoft-releases</id>
      <username>YOUR_ANYPOINT_USERNAME</username>
      <password>YOUR_ANYPOINT_PASSWORD</password>
    </server>
  </servers>
</settings>
```

### Run Tests
```bash
cd c:\Users\smend\projects\order-processing-api
mvn test
```

---

## API Reference & Sample Requests

### Health Check
```bash
curl http://localhost:8081/api/v1/health
```

### Create Order (STANDARD)
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: demo-001" \
  -d '{
    "customerId": "1",
    "items": [{"productId":"P1","name":"Widget","quantity":2,"unitPrice":25.0}],
    "totalAmount": 50.0,
    "shippingAddress": {"street":"123 Main St","city":"NYC","country":"US"}
  }'
```

### Create Order (PRIORITY - amount >= $10,000)
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "5",
    "items": [{"productId":"ENT-LIC","name":"Enterprise License","quantity":1,"unitPrice":15000.0}],
    "totalAmount": 15000.0
  }'
```

### List Orders
```bash
curl http://localhost:8081/api/v1/orders
curl "http://localhost:8081/api/v1/orders?status=PENDING"
```

### Advance Order Through Lifecycle
```bash
# Confirm → Process → Ship → Deliver
curl -X PUT http://localhost:8081/api/v1/orders/{orderId} \
  -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

### Invalid Status Transition (triggers 409 Conflict)
```bash
curl -X PUT http://localhost:8081/api/v1/orders/{orderId} \
  -H "Content-Type: application/json" \
  -d '{"status":"PENDING"}'
# Response: 409 Conflict — Invalid transition: CONFIRMED -> PENDING
```

### Order Statistics
```bash
curl http://localhost:8081/api/v1/orders/stats
```

---

## Error Response Format

All errors return a consistent JSON envelope:
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "customerId is required and cannot be blank",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

| HTTP Status | Error Code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing/invalid fields, business rule violations |
| 404 | NOT_FOUND | Order ID doesn't exist |
| 409 | CONFLICT | Invalid status transition |
| 502 | EXTERNAL_SERVICE_ERROR | JSONPlaceholder API unreachable |
| 500 | INTERNAL_ERROR | Unexpected runtime error |

---

*For authoritative MuleSoft documentation: https://docs.mulesoft.com/general/*