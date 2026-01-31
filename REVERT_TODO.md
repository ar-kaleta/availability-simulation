# Revert & Re-implementation plan (High-Performance GDS Simulation)

This file lists an ordered, iterable checklist to re-implement the availability system as a high-performance gRPC service with caching and database backing.

Checklist (iterative - run build after each numbered step)

1) Restore baseline
   - [x] Confirm `./gradlew clean build` passes with the repository as-is.
   - [x] If it fails, fix the environment until build succeeds.

2) Add dependencies
   - [x] Add `spring-kafka` and `caffeine` to `build.gradle`.
   - [ ] Add `grpc-server-spring-boot-starter`, `grpc-stub`, `protobuf-java` dependencies.
   - [ ] Configure Protobuf Gradle plugin for code generation.
   - [ ] Run `./gradlew clean build` to verify dependencies.

3) Define gRPC Interface
   - [ ] Create `src/main/proto/availability.proto`.
   - [ ] Define `AvailabilityService` with `GetAvailability` method.
   - [ ] Define messages: `AvailabilityRequest` (flight, dates, origin, dest) and `AvailabilityResponse` (availability string).
   - [ ] Run `./gradlew generateProto` and verify generated classes.

4) Domain Model & Database
   - [x] Domain records (`FlightDateKey`, `Availability`, `AvailabilityEvent`) are created.
   - [ ] Ensure `Flight` entity in `com.example.availability.simulator.data` maps correctly to the domain requirements.
   - [ ] Add `FlightRepository` (JPA) to access the database.

5) Database Seeder (Initial Population)
   - [ ] Create `DatabaseSeeder` service that runs on startup.
   - [ ] Populate the `Flight` table with a large set of mock flights (e.g., 10,000 flights) and initial availability strings.
   - [ ] This ensures the "Cache Miss -> DB Hit" path has data to return.

6) Implement Read-Through Cache Logic
   - [ ] Refactor `AvailabilityCache` or create `AvailabilityService` to implement the Read-Through pattern:
     - 1. Check Caffeine/Memory Cache.
     - 2. If miss, query `FlightRepository` (DB).
     - 3. If found in DB, populate Cache and return.
     - 4. If missing in DB, return empty/error.
   - [ ] Add unit tests for this logic (mocking Repository).

7) Implement gRPC Service
   - [ ] Create `AvailabilityGrpcService` extending the generated base class.
   - [ ] Implement `getAvailability` to call `AvailabilityService`.
   - [ ] Add error handling (gRPC status codes).
   - [ ] Run `./gradlew clean build`.

8) Kafka Consumer (Write/Update Path)
   - [ ] Implement `AvailabilityConsumer` to listen for inventory updates (e.g., "Seats Sold", "Flight Cancelled") from external systems.
   - [ ] On event: 
     - 1. Update Database (System of Record).
     - 2. Update/Invalidate Cache (Near Real-Time consistency).
   - [ ] This separates the heavy read path (gRPC) from the write path (Kafka).

9) Availability Simulator (Inventory Producer)
   - [ ] Create `InventorySimulator` service.
   - [ ] Periodically (or randomly) generate `AvailabilityEvent`s (e.g., changing "F9" to "F8").
   - [ ] Publish these events to the Kafka topic.
   - [ ] This simulates the dynamic nature of a GDS.

10) Traffic Simulator (Mock Player)
    - [ ] Create `TrafficGenerator` component.
    - [ ] Generate random `AvailabilityRequest` calls to the gRPC service.
    - [ ] Log latency and throughput stats.

11) Observability
    - [ ] Add Micrometer metrics for:
      - Cache Hits/Misses.
      - DB Read Latency.
      - gRPC Request Latency.
      - Kafka Consumer Lag.

12) Integration Testing
    - [ ] Add integration tests spinning up the full context (H2/Postgres + Kafka).
    - [ ] Verify the flow: Update via Kafka -> Check via gRPC.

13) Documentation
    - [ ] Update README with instructions on how to run the simulator and view metrics.
