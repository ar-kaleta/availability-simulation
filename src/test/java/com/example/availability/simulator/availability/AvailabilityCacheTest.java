package com.example.availability.simulator.availability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityCacheTest {

    private AvailabilityCache cache;
    private FlightDateKey key;

    @BeforeEach
    void setUp() {
        cache = new AvailabilityCache();
        key = new FlightDateKey("FL100", LocalDate.now());
    }

    @Test
    void getAvailability_returnsEmpty_whenKeyNotPresent() {
        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateAvailability_createsNewEntry() {
        AvailabilityEvent event = new AvailabilityEvent(key, 1L, 10, Instant.now());
        cache.updateAvailability(event);

        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isPresent());
        assertEquals(10, result.get().availableSeats());
    }

    @Test
    void updateAvailability_appliesDelta() {
        // Initial state
        cache.updateAvailability(new AvailabilityEvent(key, 1L, 10, Instant.now()));

        // Apply delta
        cache.updateAvailability(new AvailabilityEvent(key, 2L, -3, Instant.now()));

        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isPresent());
        assertEquals(7, result.get().availableSeats());
    }

    @Test
    void updateAvailability_ignoresDuplicateOrOlderSequence() {
        cache.updateAvailability(new AvailabilityEvent(key, 2L, 10, Instant.now()));

        // Try to apply older sequence
        cache.updateAvailability(new AvailabilityEvent(key, 1L, 5, Instant.now()));
        
        // Try to apply same sequence
        cache.updateAvailability(new AvailabilityEvent(key, 2L, 5, Instant.now()));

        assertEquals(10, cache.getAvailability(key).orElseThrow().availableSeats());
    }

    @Test
    void updateAvailability_ensuresNonNegativeSeats() {
        cache.updateAvailability(new AvailabilityEvent(key, 1L, 5, Instant.now()));
        
        // Apply large negative delta
        cache.updateAvailability(new AvailabilityEvent(key, 2L, -10, Instant.now()));

        assertEquals(0, cache.getAvailability(key).orElseThrow().availableSeats());
    }
}
