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
        key = new FlightDateKey("FL100", LocalDate.now(), "JFK", "LHR");
    }

    @Test
    void getAvailability_returnsEmpty_whenKeyNotPresent() {
        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateAvailability_createsNewEntry() {
        AvailabilityEvent event = new AvailabilityEvent(key, 1L, "F9 J9 Y9", Instant.now());
        cache.updateAvailability(event);

        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isPresent());
        assertEquals("F9 J9 Y9", result.get().availabilityString());
    }

    @Test
    void updateAvailability_updatesEntry() {
        // Initial state
        cache.updateAvailability(new AvailabilityEvent(key, 1L, "F9 J9 Y9", Instant.now()));

        // Update state
        cache.updateAvailability(new AvailabilityEvent(key, 2L, "F5 J2 Y0", Instant.now()));

        Optional<Availability> result = cache.getAvailability(key);
        assertTrue(result.isPresent());
        assertEquals("F5 J2 Y0", result.get().availabilityString());
    }

    @Test
    void updateAvailability_ignoresDuplicateOrOlderSequence() {
        cache.updateAvailability(new AvailabilityEvent(key, 2L, "F5", Instant.now()));

        // Try to apply older sequence
        cache.updateAvailability(new AvailabilityEvent(key, 1L, "F9", Instant.now()));
        
        // Try to apply same sequence
        cache.updateAvailability(new AvailabilityEvent(key, 2L, "F9", Instant.now()));

        assertEquals("F5", cache.getAvailability(key).orElseThrow().availabilityString());
    }
}
