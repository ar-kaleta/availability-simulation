# Availability Simulation

A Spring Boot application that simulates a **GDS (Global Distribution System) availability service** for airline seat inventory. It provides real-time seat availability queries with a caching layer and event-driven updates via Kafka, along with built-in traffic generation and observability.

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Inventory       │     │  Kafka           │     │  Availability    │
│  Simulator       │────▶│  availability-   │────▶│  Consumer        │
│  (seat decrements)│     │  updates         │     │  (DB + Cache)    │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
┌─────────────────┐     ┌──────────────────┐              │
│  Traffic         │     │  REST API        │◀─────────────┘
│  Generator       │────▶│  GET /availability│              │
│  (HTTP requests) │     │  (Cache → DB)    │              │
└─────────────────┘     └────────┬─────────┘              │
                                 │                         │
                          ┌──────▼───────┐         ┌───────▼──────┐
                          │   PostgreSQL  │         │   Caffeine   │
                          │   (flights)   │         │   Cache      │
                          └──────────────┘         └──────────────┘
```

## Prerequisites

- **Java 25** (or later)
- **Docker & Docker Compose** (for PostgreSQL)
- **Apache Kafka** (for event-driven inventory updates)

## How to Run

### 1. Start Infrastructure

```bash
# Start PostgreSQL
docker compose up -d

# Start Kafka (using redpanda or confluent)
# Option A: Redpanda (single-container Kafka-compatible)
docker run -d --name redpanda \
  -p 9092:9092 \
  redpandadata/redpanda:latest start \
  --kafka-addr PLAINTEXT://0.0.0.0:9092 \
  --advertise-kafka-addr PLAINTEXT://localhost:9092

# Option B: Confluent Kafka
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  confluentinc/cp-kafka:latest
```

### 2. Configure Kafka Connection

Create a `src/main/resources/application-local.properties` or set the following property:

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.bootstrap-servers=localhost:9092
```

Or pass it on the command line:

```bash
./gradlew bootRun --args='--spring.kafka.bootstrap-servers=localhost:9092'
```

### 3. Run the Application

```bash
./gradlew bootRun
```

The application starts on `http://localhost:8080`. On startup, it will:
- Seed **1000 mock flights** with random availability data
- Start the **Inventory Simulator** (publishes seat decrement events to Kafka every 5 seconds)
- Start the **Traffic Generator** (fires REST requests every 1 second and logs latency stats)

## Configuration

All simulator settings are configurable via `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `simulator.inventory.interval-ms` | `5000` | Interval between inventory event batches (ms) |
| `simulator.inventory.batch-size` | `5` | Number of flights per inventory batch |
| `simulator.traffic.interval-ms` | `1000` | Interval between traffic generation batches (ms) |
| `simulator.traffic.request-count` | `10` | Number of requests per traffic batch |
| `simulator.traffic.enabled` | `true` | Enable/disable traffic generator |

## REST API

### Query Seat Availability

```
GET /availability?flightNumber=100&origin=JFK&destination=LHR&departureDateTime=2023-10-27T10:00&arrivalDateTime=2023-10-27T12:00
```

**Response (200 OK):**
```
F9 J5 Y3
```
Format: `<CabinClass><SeatCount>` pairs separated by spaces (e.g., First: 9, Business: 5, Economy: 3).

**Response (404 Not Found):**
Empty body when the flight is not found.

## Observability

The application exposes **Prometheus metrics** and **health checks** via Spring Boot Actuator.

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health with component details |
| `GET /actuator/prometheus` | Prometheus-format metrics |

### Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `availability.cache.hits` | Counter | Cache hit count |
| `availability.cache.misses` | Counter | Cache miss count |
| `availability.cache.read` | Timer | Cache read latency |
| `availability.database.read` | Timer | Database read latency |
| `availability.rest.request` | Timer | REST request latency |
| `availability.kafka.consumer.process` | Timer | Kafka consumer processing latency |

### Viewing Metrics

```bash
# Prometheus format
curl http://localhost:8080/actuator/prometheus

# Health check
curl http://localhost:8080/actuator/health
```

### Grafana Integration

Point Grafana's Prometheus datasource to `http://localhost:8080/actuator/prometheus` and create dashboards using the metrics above. Example panel queries:

- **Cache Hit Rate**: `rate(availability_cache_hits_total[5m]) / (rate(availability_cache_hits_total[5m]) + rate(availability_cache_misses_total[5m]))`
- **REST Latency (p99)**: `histogram_quantile(0.99, rate(availability_rest_request_seconds_bucket[5m]))`
- **DB Read Latency (p95)**: `histogram_quantile(0.95, rate(availability_database_read_seconds_bucket[5m]))`

## Traffic Generator Logs

The Traffic Generator logs batch and cumulative statistics to the console:

```
[TrafficGenerator] Batch stats: requests=10, min=5ms, max=42ms, avg=12ms
[TrafficGenerator] Cumulative: total=150 requests, avg=11ms, throughput=14.2 req/s
```

## Running Tests

```bash
# Run all tests (unit + integration)
./gradlew clean build

# Run only unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "InventorySimulatorTest"
```

The integration tests use **EmbeddedKafka** and **H2** (in-memory), so no external services are needed for testing.

## Technology Stack

- **Spring Boot 3.5.7** with Java 25
- **Spring Data JPA** with PostgreSQL (runtime) / H2 (tests)
- **Spring Kafka** for event-driven updates
- **Caffeine** for in-memory caching
- **Micrometer + Prometheus** for observability
- **Lombok** for boilerplate reduction

