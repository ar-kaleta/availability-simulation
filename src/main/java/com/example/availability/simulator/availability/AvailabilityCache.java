package com.example.availability.simulator.availability;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AvailabilityCache {

    private record CachedState(Availability availability, long lastSequence) {}

    private final Map<FlightDateKey, CachedState> store = new ConcurrentHashMap<>();

    private final Cache<FlightDateKey, Availability> readCache;

    public AvailabilityCache() {
        this.readCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    public Optional<Availability> getAvailability(FlightDateKey key) {
        // Try read cache first
        Availability cached = readCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Fallback to authoritative store
        CachedState state = store.get(key);
        if (state != null) {
            readCache.put(key, state.availability());
            return Optional.of(state.availability());
        }
        
        return Optional.empty();
    }

    public void updateAvailability(AvailabilityEvent event) {
        store.compute(event.key(), (key, currentState) -> {
            if (currentState != null && event.sequenceNumber() <= currentState.lastSequence()) {
                // Idempotency / Out of order (older) check
                // Ignore older or duplicate events
                return currentState;
            }

            // Update with new state (Snapshot)
            Availability newAvail = new Availability(key, event.availabilityString());
            readCache.put(key, newAvail);
            return new CachedState(newAvail, event.sequenceNumber());
        });
    }

    public void putFromDatabase(Availability availability) {
        // When loading from DB, we treat it as the initial state (sequence 0) 
        // or just populate the read cache if we don't want to make it authoritative yet.
        // However, if we want it to be authoritative, we should put it in the store.
        // Let's assume DB load is sequence 0.
        store.computeIfAbsent(availability.key(), k -> {
            readCache.put(k, availability);
            return new CachedState(availability, 0L);
        });
        // If it's already in store, we don't overwrite because memory might have newer events (higher sequence).
    }
}
