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

    // Authoritative store: Key -> Availability (with sequence tracking)
    // We store Availability object which holds the current state.
    // However, we also need to track the last sequence number processed for this key to ensure idempotency/ordering.
    // So we might need a wrapper or just trust the Availability object if we add sequence to it.
    // But Availability record defined earlier doesn't have sequence.
    // Let's store a wrapper or extend the map value.
    
    // Actually, for simplicity and since Availability is a record, let's store a custom internal state object
    // or just the Availability and a separate sequence map? 
    // Better: Store a "CachedAvailability" that has (Availability, lastSequence).
    
    private record CachedState(Availability availability, long lastSequence) {}

    private final Map<FlightDateKey, CachedState> store = new ConcurrentHashMap<>();

    // Read cache (Caffeine) - acts as a hot cache or view. 
    // Since we have the authoritative store in memory, Caffeine here might be redundant unless we want to 
    // expire old entries or limit size. The requirement says "thin Caffeine hot-cache wrapper".
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
            if (currentState == null) {
                // New entry
                // Initial seats? The event has a delta. 
                // If we assume initial state is 0 or some default, we apply delta.
                // Let's assume we start at 0 if not present, or maybe the event implies a base?
                // Usually inventory starts with a snapshot or an initial creation event.
                // For this simulation, let's assume start at 0 + delta, but ensure non-negative.
                int newSeats = Math.max(0, event.seatDelta());
                Availability newAvail = new Availability(key, newSeats);
                readCache.put(key, newAvail);
                return new CachedState(newAvail, event.sequenceNumber());
            }

            if (event.sequenceNumber() <= currentState.lastSequence()) {
                // Idempotency / Out of order (older) check
                // Ignore older or duplicate events
                return currentState;
            }

            // Apply delta
            int newSeats = currentState.availability().availableSeats() + event.seatDelta();
            if (newSeats < 0) {
                // Handle negative inventory? Clamp to 0 or allow?
                // Requirement said "non-negative seats".
                newSeats = 0; 
            }

            Availability newAvail = new Availability(key, newSeats);
            readCache.put(key, newAvail);
            return new CachedState(newAvail, event.sequenceNumber());
        });
    }
}
