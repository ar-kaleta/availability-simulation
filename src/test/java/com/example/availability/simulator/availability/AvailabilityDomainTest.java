package com.example.availability.simulator.availability;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityDomainTest {

    @Test
    void flightDateKey_validatesInput() {
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(null, "JFK", "LHR", dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(-1, "JFK", "LHR", dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, null, "LHR", dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, "JFK", null, dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, "", "LHR", dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, "JFK", "", dep, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, "JFK", "LHR", null, arr));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(123, "JFK", "LHR", dep, null));
        
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        assertEquals(123, key.flightNumber());
        assertEquals("JFK", key.origin());
        assertEquals("LHR", key.destination());
        assertEquals(dep, key.departureDateTime());
        assertEquals(arr, key.arrivalDateTime());
    }

    @Test
    void availability_validatesInput() {
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        
        assertThrows(IllegalArgumentException.class, () -> new Availability(null, "F9"));
        assertThrows(IllegalArgumentException.class, () -> new Availability(key, null));

        Availability availability = new Availability(key, "F9 J2 Y0");
        assertEquals(key, availability.key());
        assertEquals("F9 J2 Y0", availability.availabilityString());
    }

    @Test
    void availabilityEvent_validatesInput() {
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        Instant now = Instant.now();
        
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityEvent(null, 1, "F9", now));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityEvent(key, 1, null, now));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityEvent(key, 1, "F9", null));

        AvailabilityEvent event = new AvailabilityEvent(key, 100L, "F9 J2", now);
        assertEquals(key, event.key());
        assertEquals(100L, event.sequenceNumber());
        assertEquals("F9 J2", event.availabilityString());
        assertEquals(now, event.timestamp());
    }
    
    @Test
    void valueSemantics() {
        LocalDateTime dep = LocalDateTime.of(2023, 10, 27, 10, 0);
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key1 = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        FlightDateKey key2 = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }
}
