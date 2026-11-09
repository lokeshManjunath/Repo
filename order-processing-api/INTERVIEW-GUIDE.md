# Order Processing API — Interview Preparation Guide

**Project:** `order-processing-api`  
**Runtime:** Mule 4.10.0 | Java 11 | MUnit 3.6.1  
**Location:** `c:\Users\smend\projects\order-processing-api`

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Project File Structure](#3-project-file-structure)
4. [File-by-File Explanation](#4-file-by-file-explanation)
   - [Maven & Configuration](#41-maven--configuration)
   - [RAML API Specification](#42-raml-api-specification)
   - [Mule XML Flows](#43-mule-xml-flows)
   - [Java Components](#44-java-components)
   - [MUnit Tests](#45-munit-tests)
5. [MuleSoft Concepts Demonstrated](#5-mulesoft-concepts-demonstrated)
6. [Interview Q&A](#6-interview-qa)
7. [How to Run](#7-how-to-run)
8. [Sample API Calls](#8-sample-api-calls)

---

## 1. Project Overview

A **RESTful Order Processing API** built on MuleSoft 4 that demonstrates every concept required by the interview specification:

| Requirement | Implementation |
|---|---|
| **Java Component** | `OrderValidator.validate()` — validates business rules |
| **Java Transformer** | `OrderUtils.buildAuditEntry()`, `calculateOrderMetrics()` |
| **Java Filter** | `OrderValidator.isValidStatusTransition()` — state-machine guard |
| **Process Orchestration** | Scatter-Gather, Choice Router, Async VM Queue, Flow-ref |
| **Data Transformation** | 8 DataWeave 2.0 transforms across all flows |
| **Error Handling** | Global handler (5 types) + flow-level try/catch |
| **External Integration** | JSONPlaceholder REST API (simulates CRM) |
| **Async Messaging** | VM Queue with `vm:listener` (event-driven) |
| **Testing** | 13 MUnit 3.6.1 tests |
| **Originality** | Custom state machine, parallel enrichment, priority routing |

---

## 2. Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                      EXPERIENCE LAYER                         │
│                        api.xml                                │
│   GET  /api/v1/health                                         │
│   POST /api/v1/orders          ← Main orchestration flow      │
│   GET  /api/v1/orders                                         │
│   GET  /api/v1/orders/{orderId}                               │
│   PUT  /api/v1/orders/{orderId}                               │
│   DELETE /api/v1/orders/{orderId}                             │
│   GET  /api/v1/orders/stats                                   │
└────────────────────────┬──────────────────────────────────────┘
                         │  flow-ref (delegate)
┌────────────────────────▼──────────────────────────────────────┐
│                      PROCESS LAYER                            │
│              order-impl.xml + order-subflows.xml              │
│                                                               │
│  create-order-impl-flow:                                      │
│    1. Java  — OrderValidator.validate()     [validation]      │
│    2. Java  — OrderValidator.generateOrderId()               │
│    3. Scatter-Gather ─────────────────────  [parallel]        │
│       ├─ Route A: customer-enrichment-subflow (HTTP→CRM)      │
│       └─ Route B: OrderUtils.buildAuditEntry() [transform]    │
│    4. DataWeave  — build order object       [transform]       │
│    5. Choice Router — PRIORITY/EXPRESS/STD  [orchestration]   │
│    6. Java  — OrderRepository.save()        [persist]         │
│    7. Async — vm:publish (fire-and-forget)  [async]           │
│    8. DataWeave  — build 201 response                         │
│                                                               │
│  order-notification-consumer-flow (vm:listener):             │
│    Event-driven, 2 consumers, redelivery-policy(3x), DLQ     │
└────────────────────────┬──────────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────────┐
│                      SYSTEM LAYER                             │
│   OrderRepository (ConcurrentHashMap)                         │
│   JSONPlaceholder API (jsonplaceholder.typicode.com)          │
└───────────────────────────────────────────────────────────────┘
```

### Order Priority Rules
```
totalAmount  < $1,000   → STANDARD  → STANDARD_LANE
totalAmount $1K–$9,999  → EXPRESS   → EXPRESS_LANE
totalAmount ≥ $10,000   → PRIORITY  → PRIORITY_LANE
```

### Order Status State Machine
```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
    ↓           ↓           ↓
CANCELLED  CANCELLED  CANCELLED
```
Terminal states: `DELIVERED`, `CANCELLED`

---

## 3. Project File Structure

```
order-processing-api/
├── pom.xml                               Maven build + dependencies
├── mule-artifact.json                    Runtime config (Mule 4.10, Java 11)
├── INTERVIEW-GUIDE.md                    This document
│
├── src/main/
│   ├── java/com/mulesoft/interview/
│   │   ├── OrderValidator.java           Java Component + Filter
│   │   ├── OrderUtils.java               Java Transformer + Utility
│   │   └── OrderRepository.java          Java Data Access (in-memory store)
│   │
│   ├── mule/
│   │   ├── api.xml                       Experience Layer: HTTP listeners
│   │   ├── global-config.xml             Global configs + error handler
│   │   ├── order-impl.xml                Impl flows 1-4 (health/create/list/get)
│   │   └── order-subflows.xml            Impl flows 5-8 + enrichment + consumer
│   │
│   └── resources/
│       ├── application.yaml              Property placeholders
│       ├── log4j2.xml                    Logging config
│       └── api/
│           ├── order-processing-api.raml RAML 1.0 root spec
│           └── dataTypes/                RAML DataType fragments
│               ├── OrderItem.raml
│               ├── ShippingAddress.raml
│               ├── OrderRequest.raml
│               ├── StatusUpdateRequest.raml
│               ├── OrderResponse.raml
│               ├── OrderListResponse.raml
│               ├── OrderStatsResponse.raml
│               └── ErrorResponse.raml
│
└── src/test/munit/
    └── order-api-test-suite.xml          13 MUnit tests
```

---

## 4. File-by-File Explanation

---

### 4.1 Maven & Configuration

#### `pom.xml`
The Maven build descriptor. Key sections:

| Section | Value | Why |
|---|---|---|
| `app.runtime` | `4.10.0` | Mule EE runtime version |
| `munit.version` | `3.6.1` | MUnit test framework version |
| `mule-http-connector` | `1.9.3` | HTTP listener + outbound requests |
| `mule-vm-connector` | `2.0.4` | Async in-memory messaging |
| `mule-java-module` | `2.0.8` | Invokes Java classes from flows |
| `munit-runner/tools` | `3.6.1` | Test runner + assertion library |

**Interview point:** The `<classifier>mule-plugin</classifier>` marks a dependency as a Mule connector (packaged differently from regular JARs — it's a ZIP with its own classloader).

---

#### `mule-artifact.json`
Tells the Mule runtime about the application:
- `minMuleVersion: 4.10.0` — minimum runtime required
- `javaSpecificationVersions: ["11"]` — Java 11 bytecode
- `exportedPackages: ["com.mulesoft.interview"]` — makes custom Java classes visible to the Java Module connector

**Interview point:** Mule 4 uses classloader isolation. Without `exportedPackages`, the Java Module cannot find your custom classes.

---

#### `src/main/resources/application.yaml`
All property placeholders. Referenced in Mule XML as `${property.name}`.

```yaml
http:
  port: "8081"
external:
  jsonplaceholder:
    host: "jsonplaceholder.typicode.com"
order:
  notification:
    queue-name: "order-notifications"
```

Loaded by `<configuration-properties file="application.yaml"/>` in `global-config.xml`.

**Interview point:** Never hardcode environment-specific values (ports, hostnames, credentials) in flow XML. Use property files so the same artifact runs in DEV, UAT, and PROD by swapping properties.

---

### 4.2 RAML API Specification

#### `src/main/resources/api/order-processing-api.raml`
The **API contract** — defines what the API does before any code is written (API-first design).

Key sections:
```raml
#%RAML 1.0
title: Order Processing API
version: v1
baseUri: http://localhost:8081/api/{version}

types:
  OrderRequest:  !include dataTypes/OrderRequest.raml
  OrderResponse: !include dataTypes/OrderResponse.raml
  ErrorResponse: !include dataTypes/ErrorResponse.raml
  ...

/orders:
  post:  { type: OrderRequest  → 201: OrderResponse, 400: ErrorResponse }
  get:   { → 200: OrderListResponse }
  /stats:
    get: { → 200: OrderStatsResponse }
  /{orderId}:
    get:    { → 200: OrderResponse, 404: ErrorResponse }
    put:    { type: StatusUpdateRequest → 200/404/409 }
    delete: { → 204, 404 }
```

#### `src/main/resources/api/dataTypes/` (8 files)

Each type is a separate `#%RAML 1.0 DataType` fragment. This keeps the root RAML clean and makes types reusable.

| File | Purpose |
|---|---|
| `OrderItem.raml` | Single product line-item (productId, name, quantity, unitPrice) |
| `ShippingAddress.raml` | Delivery address (street, city, country, zipCode?) |
| `OrderRequest.raml` | POST body — references OrderItem and ShippingAddress via `!include` |
| `StatusUpdateRequest.raml` | PUT body — status enum with state-machine docs |
| `OrderResponse.raml` | 201/200 response — includes priority, processingLane, HATEOAS links |
| `OrderListResponse.raml` | GET /orders response — total, filter, orders array |
| `OrderStatsResponse.raml` | GET /orders/stats — summary metrics + byStatus breakdown |
| `ErrorResponse.raml` | Standard error envelope — code enum, message, requestId |

**Interview point:** `OrderRequest.raml` cross-references other fragments using `!include OrderItem.raml` within the `dataTypes/` folder, demonstrating RAML fragment composition.

---

### 4.3 Mule XML Flows

#### `src/main/mule/global-config.xml`
Contains all **global (shared) configuration** elements — nothing business-logic-specific:

| Element | Purpose |
|---|---|
| `<configuration-properties>` | Loads `application.yaml` at startup |
| `<http:listener-config>` | Binds REST API to port 8081 |
| `<http:request-config>` | Points to JSONPlaceholder (HTTPS, basePath=/users) |
| `<vm:config>` | Creates `order-notifications` queue (bounded at 500 messages) |
| `<error-handler name="global-error-handler">` | Maps APP:VALIDATION→400, APP:NOT_FOUND→404, APP:CONFLICT→409, HTTP:CONNECTIVITY→502, ANY→500 |

**Interview point:** The global error handler uses `on-error-propagate` (not `on-error-continue`). Propagate re-raises the error after setting the response payload — this is essential so the HTTP listener can emit the correct status code via `http:error-response`.

---

#### `src/main/mule/api.xml` ← Experience Layer
Contains **7 HTTP listener flows**, one per RAML resource+method. Each flow has:
1. An `http:listener` (bound to the route path and allowed method)
2. A `flow-ref` to the corresponding implementation flow
3. `http:response` / `http:error-response` configuration for status codes

This file has **no business logic** — it's pure routing. This separation follows **API-led connectivity**:
- `api.xml` = **Experience Layer** (protocol handling)
- `order-impl.xml` + `order-subflows.xml` = **Process Layer** (business logic)

---

#### `src/main/mule/order-impl.xml` ← Process Layer (Flows 1-4)

**`health-check-impl-flow`**
- Calls `OrderUtils.getCurrentTimestamp()` via Java Module
- DataWeave builds a JSON health response with endpoint catalog

**`create-order-impl-flow`** — the main orchestration showcase (8 steps):

| Step | Component | What it does |
|---|---|---|
| 1 | `java:invoke-static` | `OrderValidator.validate()` — checks all fields, returns errors list |
| 2 | `choice` | Validation Gate — raises `APP:VALIDATION` if invalid |
| 3 | `java:invoke-static` | `OrderValidator.generateOrderId()` — creates `ORD-{ts}-{rnd}` |
| 4 | `scatter-gather` | Parallel: Route A = customer enrichment; Route B = audit entry |
| 5 | Route A → `flow-ref` | `customer-enrichment-subflow` calls JSONPlaceholder REST API |
| 6 | Route B → `java:invoke-static` | `OrderUtils.buildAuditEntry()` — Java transformer |
| 7 | `set-variable` | DataWeave merges enriched customer + audit into order object |
| 8 | `choice` | Priority Router — PRIORITY_LANE / EXPRESS_LANE / STANDARD_LANE |
| 9 | `java:invoke-static` | `OrderRepository.save()` — persists to ConcurrentHashMap |
| 10 | `async > vm:publish` | Fire-and-forget notification to VM queue |
| 11 | `set-payload` | DataWeave builds 201 Created response |

**`list-orders-impl-flow`**
- `OrderRepository.findAll()` returns all orders sorted by `createdAt` descending
- DataWeave applies optional `?status=` filter (case-insensitive) and maps to summary view

**`get-order-impl-flow`**
- `OrderRepository.findById()` — returns null if not found
- Choice router: null → `raise-error APP:NOT_FOUND`
- DataWeave builds full order detail with items, audit trail, HATEOAS links

---

#### `src/main/mule/order-subflows.xml` ← Process Layer (Flows 5-8 + Subflows)

**`update-order-impl-flow`** (PUT /orders/{orderId})
1. `OrderRepository.findById()` — find existing order (404 if missing)
2. `OrderValidator.isValidStatusTransition()` — enforce state machine (409 if invalid)
3. `OrderUtils.buildAuditEntry()` — Java transformer records the status change
4. DataWeave merges updated status + new audit entry into existing order
5. `OrderRepository.save()` — persist

**`delete-order-impl-flow`** (DELETE /orders/{orderId})
1. `OrderRepository.exists()` — check (404 if missing)
2. `OrderRepository.delete()` — remove
3. Returns null payload (204 No Content)

**`order-stats-impl-flow`** (GET /orders/stats)
1. `OrderRepository.findAll()` — all orders
2. `OrderUtils.calculateOrderMetrics()` — **Java transformer**: counts by status, sums revenue, calculates average
3. DataWeave builds structured stats response

**`customer-enrichment-subflow`** (sub-flow — no source)
- `http:request` → GET `https://jsonplaceholder.typicode.com/users/{customerId}`
- DataWeave maps external response to internal customer profile (id, name, email, phone, company, address)
- Called from `create-order-impl-flow` inside Scatter-Gather Route A
- Parent flow's try/catch provides graceful degradation if the API is unreachable

**`order-notification-consumer-flow`** (VM consumer)
- `vm:listener` — **event-driven** (not scheduler+vm:consume which is a polling anti-pattern)
- `numberOfConsumers="2"` — 2 concurrent threads process messages
- `redelivery-policy maxRedeliveryCount="3"` — retry 3x on transient failures
- `MULE:REDELIVERY_EXHAUSTED` → DLQ logger (replace with Anypoint MQ publish in production)
- Inner `choice` routes notification log messages by priority

---

### 4.4 Java Components

#### `OrderValidator.java`

| Method | Type | Purpose |
|---|---|---|
| `validate(Map orderData)` | **Java Component** | Validates all fields; returns `{valid, errors[], priority, parsedAmount}` |
| `generateOrderId()` | **Java Component** | Returns `ORD-{timestamp}-{random4}` |
| `isValidStatusTransition(String, String)` | **Java Filter** | Enforces state machine; returns boolean |
| `determinePriority(double)` | Private helper | STANDARD / EXPRESS / PRIORITY thresholds |

**Key design decisions:**
- All methods are `static` — invoked via `java:invoke-static`, no instance lifecycle to manage
- `TRANSITIONS` map is `Collections.unmodifiableMap` — thread-safe, initialized once at class load
- Validation accumulates ALL errors (not fail-fast) — better UX for API consumers

---

#### `OrderUtils.java`

| Method | Type | Purpose |
|---|---|---|
| `getCurrentTimestamp()` | Utility | ISO-8601 timestamp with timezone |
| `buildAuditEntry(String, String, String)` | **Java Transformer** | Creates immutable audit record with UUID |
| `calculateOrderMetrics(List)` | **Java Transformer** | Counts by status, sums revenue, computes avg |
| `maskEmail(String)` | Utility | PII masking: `alice@test.com` → `a****@test.com` |

**Key design decisions:**
- `REVENUE_STATUSES = List.of(...)` excludes CANCELLED orders from revenue — correct business rule
- `round2()` rounds to 2 decimal places — currency-safe arithmetic

---

#### `OrderRepository.java`

| Method | Purpose |
|---|---|
| `save(String, Map)` | Create or update (upsert) |
| `findById(String)` | Returns null if not found (no exception) |
| `findAll()` | Returns unmodifiable list, sorted newest-first |
| `exists(String)` | Boolean check without fetching full object |
| `delete(String)` | Returns boolean (true = was present and removed) |
| `clearAll()` | For MUnit test teardown only |

**Key design decision:** Uses `ConcurrentHashMap` — thread-safe for concurrent HTTP requests without explicit synchronization.

**Production note:** Replace with Anypoint ObjectStore v2 (persistent) or JDBC Connector (relational DB). The interface is the same — only the `save/findById` implementations change.

---

### 4.5 MUnit Tests

#### `src/test/munit/order-api-test-suite.xml`

13 tests covering flows and Java components directly:

| # | Test Name | What it verifies |
|---|---|---|
| 1 | `test-health-check` | Returns `status: UP` and app name |
| 2 | `test-create-order-standard` | Happy path: PENDING status, STANDARD priority, STANDARD_LANE |
| 3 | `test-create-order-priority` | Amount ≥ $10k → PRIORITY + PRIORITY_LANE |
| 4 | `test-create-order-no-customer` | Missing customerId → `APP:VALIDATION` |
| 5 | `test-create-order-empty-items` | Empty items → `APP:VALIDATION` |
| 6 | `test-list-orders` | Returns `{total, orders}` structure |
| 7 | `test-get-order-not-found` | Unknown ID → `APP:NOT_FOUND` |
| 8 | `test-java-validator-valid` | OrderValidator happy path — Java unit test |
| 9 | `test-java-validator-express` | $1,600 → EXPRESS priority |
| 10 | `test-java-valid-transition` | PENDING → CONFIRMED = true |
| 11 | `test-java-invalid-transition` | SHIPPED → PENDING = false |
| 12 | `test-java-repository-roundtrip` | Save then findById returns same data |
| 13 | `test-order-stats` | Returns `{summary, byStatus}` structure |

**Mocking strategy:**
- `http:request` is mocked via `munit-tools:mock-when processor="http:request"` — prevents real calls to JSONPlaceholder
- `vm:publish` is mocked — prevents actual VM queue operations during tests
- Java operations (`OrderRepository`, `OrderValidator`) are NOT mocked — they test the real Java code

---

## 5. MuleSoft Concepts Demonstrated

### Scatter-Gather (Parallel Fan-Out)
```xml
<scatter-gather timeout="8000">
    <route> ... customer enrichment ... </route>
    <route> ... audit entry generation ... </route>
</scatter-gather>
<set-variable value="#[payload[0] default {}]"/>
<set-variable value="#[payload[1] default {}]"/>
```
- Both routes execute **concurrently** — total time = max(route1, route2) not sum
- `payload[0]` = first route result, `payload[1]` = second route result
- Wrapped in try/catch for graceful degradation

### Choice Router (Content-Based Routing — EIP)
```xml
<choice>
    <when expression="#[vars.orderData.priority == 'PRIORITY']">...</when>
    <when expression="#[vars.orderData.priority == 'EXPRESS']">...</when>
    <otherwise>...</otherwise>
</choice>
```
Routes order to different processing lanes based on `totalAmount`.

### Async Fire-and-Forget
```xml
<async>
    <try>
        <vm:publish .../>
        <error-handler><on-error-continue type="ANY">...</on-error-continue></error-handler>
    </try>
</async>
```
The HTTP response is returned **immediately** — notification processing happens in a separate thread.

### Global Error Handler
```xml
<error-handler name="global-error-handler">
    <on-error-propagate type="APP:VALIDATION">  → 400 </on-error-propagate>
    <on-error-propagate type="APP:NOT_FOUND">   → 404 </on-error-propagate>
    <on-error-propagate type="APP:CONFLICT">    → 409 </on-error-propagate>
    <on-error-propagate type="HTTP:CONNECTIVITY"> → 502 </on-error-propagate>
    <on-error-propagate type="ANY">             → 500 </on-error-propagate>
</error-handler>
```
Referenced by all flows via `<error-handler ref="global-error-handler"/>` — DRY principle.

### VM Listener vs Scheduler
```
vm:listener   = event-driven (correct)    — fires when message arrives
scheduler     = polling (anti-pattern)    — fires on fixed timer regardless of messages
```
`vm:listener numberOfConsumers="2"` processes messages as they arrive with 2 concurrent threads.

### Java Module Invocation
```xml
<java:invoke-static
    class="com.mulesoft.interview.OrderValidator"
    method="validate(Map)"
    target="validationResult">
    <java:args>#[{ orderData: payload }]</java:args>
</java:invoke-static>
```
- `class` = fully qualified Java class name
- `method` = method signature with parameter types
- `target` = Mule variable to store the return value
- `java:args` = DataWeave map of parameter name → value (requires `-parameters` compiler flag)

### DataWeave 2.0 Transformations

DataWeave is Mule 4's built-in data transformation language. Used in 8 places in this project:

```dataweave
%dw 2.0
output application/json
var sp = vars.statusFilter
var raw = vars.allOrders default []
var list = if (sp != null) raw filter ($.status == upper(sp)) else raw
---
{
    total: sizeOf(list),
    orders: list map (o) -> {
        orderId: o.orderId,
        status: o.status,
        links: { self: '/api/v1/orders/' ++ o.orderId }
    }
}
```
Key features used: `filter`, `map`, `sizeOf`, `upper()`, `default`, string concatenation with `++`, `now()`, type coercion with `as String`.

### API-Led Connectivity

```
Experience Layer  →  api.xml             (HTTP protocol, routing)
Process Layer     →  order-impl.xml      (orchestration, validation)
                     order-subflows.xml  (enrichment, state, stats)
System Layer      →  OrderRepository     (data store)
                     JSONPlaceholder     (external CRM)
```

---

## 6. Interview Q&A

**Q: What is the purpose of `mule-artifact.json`?**

> It defines the application metadata for the Mule runtime: minimum Mule version, required Java version, and which Java packages should be exported so connectors (like the Java Module) can access them. Without `exportedPackages`, `java:invoke-static` fails with a ClassNotFoundException because Mule 4 uses strict classloader isolation per plugin.

---

**Q: Why use `on-error-propagate` instead of `on-error-continue` in the global error handler?**

> `on-error-propagate` re-raises the error after the handler runs, which allows the enclosing HTTP listener to detect that an error occurred and emit the correct HTTP status code via `http:error-response`. `on-error-continue` swallows the error — the HTTP listener would return 200 OK even after setting `vars.httpStatus = 404`, because it wouldn't know an error happened.

---

**Q: How does Scatter-Gather work and what does `payload[0]` mean after it?**

> Scatter-Gather executes all routes concurrently in separate threads. After all routes complete, it aggregates the results. The resulting `payload` is a List where each element corresponds to one route's output payload — `payload[0]` is Route A's payload, `payload[1]` is Route B's payload. Total latency = max(routeA, routeB), not sum.

---

**Q: Why `vm:listener` instead of `scheduler + vm:consume`?**

> A queue is event-driven by nature — messages arrive at unpredictable rates. `vm:listener` fires immediately when a message is available, with `numberOfConsumers` concurrent threads. `scheduler + vm:consume` polls on a fixed timer regardless of queue depth — when the queue fills faster than the scheduler fires, messages accumulate and cause OOM. Additionally, `vm:consume` can only dequeue one message per tick with no concurrent processing capability.

---

**Q: What is `redelivery-policy` and when does `MULE:REDELIVERY_EXHAUSTED` fire?**

> `redelivery-policy` on a `vm:listener` tracks how many times the same message has failed processing. After `maxRedeliveryCount` failures, Mule raises `MULE:REDELIVERY_EXHAUSTED`, which the error handler routes to a Dead Letter Queue (DLQ). Without this, failed messages are silently discarded — a compliance and data-integrity risk in production.

---

**Q: How does the state machine work in `OrderValidator.isValidStatusTransition()`?**

> A static `TRANSITIONS` Map defines allowed next statuses for each current status. The method looks up the current status, gets the allowed list, and checks whether the requested new status is in that list. The map is `Collections.unmodifiableMap` — thread-safe and initialized once at class load. This enforces business rules like "you cannot go from SHIPPED back to PENDING" at the Java layer, keeping the Mule flow clean.

---

**Q: How are RAML DataType fragments composed?**

> The root RAML uses `!include dataTypes/OrderItem.raml` to pull in each type fragment. `OrderRequest.raml` itself uses `!include OrderItem.raml` for its `items` array and `!include ShippingAddress.raml` for the address field — demonstrating nested fragment composition. This is the RAML equivalent of importing types from a library, keeping each type definition in one place.

---

**Q: How do you handle errors when the external API (JSONPlaceholder) is down?**

> The `customer-enrichment-subflow` call is wrapped in a `<try>` block inside Scatter-Gather Route A. If the HTTP call fails (timeout, connection refused), `on-error-continue type="ANY"` catches it and sets a fallback customer profile `{ name: 'Customer ?', email: 'N/A', enriched: false }`. The order creation continues successfully — this is the **graceful degradation** pattern. Without this, a single CRM outage would block all order creation.

---

**Q: What would you change for production readiness?**

| Concern | Current | Production |
|---|---|---|
| Persistence | ConcurrentHashMap (in-memory, lost on restart) | Anypoint ObjectStore v2 or JDBC |
| Async messaging | VM queue (in-memory, lost on restart) | Anypoint MQ or ActiveMQ JMS |
| Dead Letter Queue | Logger only | Real DLQ (Anypoint MQ) with alerting |
| Authentication | None | OAuth2 via Anypoint API Manager policies |
| Configuration | application.yaml | Anypoint Secrets Manager |
| Observability | Logger | Anypoint Monitoring + custom dashboards |
| CRM | JSONPlaceholder (mock) | Real Salesforce/SAP connector |

---

## 7. How to Run

### Prerequisites
- Anypoint Code Builder (VS Code extension) — OR — Anypoint Studio 7.x
- Java JDK 11+
- Anypoint Platform credentials (for Maven dependency download)

### Step 1 — Configure Maven credentials
Add to `~/.m2/settings.xml`:
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

### Step 2 — Open and Run
1. Open `c:\Users\smend\projects\order-processing-api` in VS Code with ACB
2. ACB auto-detects the Mule project
3. Press **F5** → Run Mule Application
4. Wait for: `Mule is up and kicking`
5. API available at `http://localhost:8081/api/v1/`

### Step 3 — Run Tests
```bash
cd c:\Users\smend\projects\order-processing-api
mvn test
# HTML coverage report: target/site/munit/coverage/
```

---

## 8. Sample API Calls

### Health Check
```bash
curl http://localhost:8081/api/v1/health
```
```json
{ "status": "UP", "app": "Order Processing API", "version": "1.0.0" }
```

---

### Create a STANDARD Order ($50)
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
```json
{
  "orderId": "ORD-1731058800000-4521",
  "status": "PENDING",
  "priority": "STANDARD",
  "processingLane": "STANDARD_LANE",
  "totalAmount": 50.0,
  "customerName": "Leanne Graham",
  "itemCount": 1,
  "links": { "self": "/api/v1/orders/ORD-1731058800000-4521" }
}
```

---

### Create a PRIORITY Order ($15,000)
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "2",
    "items": [{"productId":"ENT-LIC","name":"Enterprise License","quantity":1,"unitPrice":15000.0}],
    "totalAmount": 15000.0
  }'
```
Response: `"priority": "PRIORITY", "processingLane": "PRIORITY_LANE"`

---

### Advance Order Through Lifecycle
```bash
# PENDING → CONFIRMED
curl -X PUT http://localhost:8081/api/v1/orders/ORD-1731058800000-4521 \
  -H "Content-Type: application/json" \
  -d '{"status": "CONFIRMED"}'

# CONFIRMED → PROCESSING
curl -X PUT http://localhost:8081/api/v1/orders/ORD-1731058800000-4521 \
  -H "Content-Type: application/json" \
  -d '{"status": "PROCESSING"}'
```

---

### Trigger a 409 Conflict (invalid transition)
```bash
curl -X PUT http://localhost:8081/api/v1/orders/ORD-1731058800000-4521 \
  -H "Content-Type: application/json" \
  -d '{"status": "PENDING"}'
```
```json
{ "error": { "code": "CONFLICT", "message": "Invalid transition: CONFIRMED -> PENDING" } }
```

---

### List Orders with Filter
```bash
curl "http://localhost:8081/api/v1/orders?status=PENDING"
```

---

### Order Statistics
```bash
curl http://localhost:8081/api/v1/orders/stats
```
```json
{
  "summary": { "totalOrders": 3, "totalRevenue": 15050.0, "avgOrderValue": 5016.67 },
  "byStatus": { "PENDING": 1, "CONFIRMED": 1, "PROCESSING": 1, "SHIPPED": 0, "DELIVERED": 0, "CANCELLED": 0 }
}
```

---

*For authoritative MuleSoft documentation: https://docs.mulesoft.com/general/*
