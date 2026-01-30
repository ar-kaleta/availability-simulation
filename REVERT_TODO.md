# Revert & Re-implementation plan (start from a successful build)

This file lists an ordered, iterable checklist to re-implement the availability cache system, but only after the project builds cleanly. Follow these steps one-by-one and run the build/test after each major change.

Checklist (iterative - run build after each numbered step)

1) Restore baseline
   - [x] Confirm `./gradlew clean build` passes with the repository as-is (no changes).
   - [x] If it fails, fix the environment until build succeeds. Do not add features until the build is green.

2) Add dependencies (small, compile-safe)
   - [x] Add `spring-kafka` and `caffeine` to `build.gradle` dependencies.
   - [x] Run `./gradlew clean build` and confirm compile succeeds and dependencies download.

3) Create domain model (small, unit-testable)
   - [x] Add `FlightDateKey`, `Availability`, `AvailabilityEvent` under `src/main/java/com/example/availability/simulator/availability`.
   - [x] Add unit tests for value semantics (equals/hashCode, basic constructors).
   - [x] Run `./gradlew clean test`.

4) Implement the in-memory cache
   - [x] Implement `AvailabilityCache` using `ConcurrentHashMap` as authoritative store.
   - [x] Add a thin `Caffeine` hot-cache wrapper for reads (configurable TTL/size).
   - [x] Add unit tests for event application: sequence handling, idempotency, delta application, non-negative seats.
   - [x] Run `./gradlew clean test`.

5) Add service and controller
   - [ ] Add `AvailabilityService` that wraps cache operations.
   - [ ] Add `AvailabilityController` with `GET /availability?flightId=&date=` returning cached value (no I/O on request path).
   - [ ] Add controller unit tests (MockMvc) for happy path and not-found.
   - [ ] Run `./gradlew clean test`.

6) Add Kafka consumer configuration
   - [ ] Add `KafkaConfig` with `ConsumerFactory<String,String>` and `ConcurrentKafkaListenerContainerFactory`.
   - [ ] Keep value deserializer as String (parse JSON via Jackson in the listener) for explicit control during parsing and validation.
   - [ ] Run `./gradlew clean build`.

7) Implement Kafka consumer
   - [ ] Add `AvailabilityConsumer` annotated with `@KafkaListener` that parses JSON into `AvailabilityEvent` and calls `AvailabilityService.handleEvent`.
   - [ ] Add basic error handling (log + optional DLQ placeholder).
   - [ ] Add unit tests for parsing and delegation (mock service).
   - [ ] Run `./gradlew clean test`.

8) Implement startup replay (opt-in)
   - [ ] Add `StartupReplayService` that on `ApplicationReadyEvent` (conditional on `app.replay.enabled=true`) replays topic from earliest to rebuild state.
   - [ ] Implement replay as a separate consumer that reads in batches and calls `AvailabilityService.handleEvent`.
   - [ ] Make replay idempotent and resumable via offsets/metrics/logging.
   - [ ] Run integration tests using `spring-kafka-test` with `@EmbeddedKafka`.

9) Observability & safety
   - [ ] Add metrics (Micrometer counters for events processed, replay progress, cache hits/misses).
   - [ ] Add structured logs for event application (debug) and warnings for out-of-order states.
   - [ ] Add answerable errors and health checks for missing Kafka connectivity.

10) Integration testing & validation
   - [ ] Add Embedded Kafka integration tests that publish events and assert REST responses reflect expected availability.
   - [ ] Run `./gradlew clean test` and ensure integration tests pass locally.

11) Documentation
   - [ ] Update `README.md` with run instructions, config properties, endpoints, and how to run integration tests.

Notes / Tips
- Make small commits and run the build after each step to catch issues early.
- Keep the consumer simple: validate JSON strictly, log & drop bad messages, optionally route to DLQ later.
- For production, consider replacing single-node in-memory cache with a shared store (Redis/Consul) for multi-instance deployments, or add snapshotting to speed restarts.

If you want, I can now:
- Fully remove any assistant-created files that still exist (I already replaced many with notes above) and re-run `./gradlew clean build` to verify the repo returns to original build state.
- Or start the re-implementation following this checklist, step-by-step, running builds/tests after each change.

Tell me which you prefer (verify revert/build or proceed to re-implement).
