# Revert & Re-implementation plan (High-Performance GDS Simulation - REST)

This file lists an ordered, iterable checklist to re-implement the availability system as a high-performance REST service with caching and database backing.

Checklist (iterative - run build after each numbered step)

1) Restore baseline
   - [x] Confirm `./gradlew clean build` passes with the repository as-is.
   - [x] If it fails, fix the environment until build succeeds.

2) Add dependencies
   - [x] Add `spring-kafka` and `caffeine` to `build.gradle`.
   - [x] Run `./gradlew clean build` to verify dependencies.

3) Domain Model & Database
   - [x] Domain records (`FlightDateKey`, `Availability`, `AvailabilityEvent`) are created.
   - [x] Ensure `Flight` entity in `com.example.availability.simulator.data` maps correctly to the domain requirements.
   - [x] Add `FlightRepository` (JPA) to access the database.

4) Database Seeder (Initial Population)
    - [x] Create `DatabaseSeeder` service that runs on startup.
    - [x] Populate the `Flight` table with a large set of mock flights (e.g., 10,000 flights) and initial availability
      strings.
    - [x] This ensures the "Cache Miss -> DB Hit" path has data to return.

5) Implement Read-Through Cache Logic
   - [ ] Refactor `AvailabilityCache` or create `AvailabilityService` to implement the Read-Through pattern:
     - 1. Check Caffeine/Memory Cache.
     - 2. If miss, query `FlightRepository` (DB).
     - 3. If found in DB, populate Cache and return.
     - 4. If missing in DB, return empty/error.
   - [ ] Add unit tests for this logic (mocking Repository).

6) Implement REST Controller
   - [ ] Create `AvailabilityController`.
   - [ ] Implement `GET /availability` endpoint taking flight details as params.
   - [ ] Call `AvailabilityService`.
   - [ ] Run `./gradlew clean build`.

7) Kafka Consumer (Write/Update Path)
   - [ ] Implement `AvailabilityConsumer` to listen for inventory updates (e.g., "Seats Sold", "Flight Cancelled") from external systems.
   - [ ] On event: 
     - 1. Update Database (System of Record).
     - 2. Update/Invalidate Cache (Near Real-Time consistency).
   - [ ] This separates the heavy read path (REST) from the write path (Kafka).

8) Availability Simulator (Inventory Producer)
   - [ ] Create `InventorySimulator` service.
   - [ ] Periodically (or randomly) generate `AvailabilityEvent`s (e.g., changing "F9" to "F8").
   - [ ] Publish these events to the Kafka topic.
   - [ ] This simulates the dynamic nature of a GDS.

9) Traffic Simulator (Mock Player)
   - [ ] Create `TrafficGenerator` component.
   - [ ] Generate random REST requests to the `AvailabilityController`.
   - [ ] Log latency and throughput stats.

10) Observability
    - [ ] Add Micrometer metrics for:
      - Cache Hits/Misses.
      - DB Read Latency.
      - REST Request Latency.
      - Kafka Consumer Lag.

11) Integration Testing
    - [ ] Add integration tests spinning up the full context (H2/Postgres + Kafka).
    - [ ] Verify the flow: Update via Kafka -> Check via REST.

12) Documentation
    - [ ] Update README with instructions on how to run the simulator and view metrics.
