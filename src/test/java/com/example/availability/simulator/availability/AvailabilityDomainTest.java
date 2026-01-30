package com.example.availability.simulator.availability;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityDomainTest {

    @Test
    void flightDateKey_validatesInput() {
        LocalDate date = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey(null, date));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey("", date));
        assertThrows(IllegalArgumentException.class, () -> new FlightDateKey("FL123", null));
        
        FlightDateKey key = new FlightDateKey("FL123", date);
        assertEquals("FL123", key.flightNumber());
        assertEquals(date, key.date());
    }

    @Test
    void availability_validatesInput() {
        FlightDateKey key = new FlightDateKey("FL123", LocalDate.now());
        assertThrows(IllegalArgumentException.class, () -> new Availability(null, 10));
        assertThrows(IllegalArgumentException.class, () -> new Availability(key, -1));

        Availability availability = new Availability(key, 5);
        assertEquals(key, availability.key());
        assertEquals(5, availability.availableSeats());
    }

    @Test
    void availabilityEvent_validatesInput() {
        FlightDateKey key = new FlightDateKey("FL123", LocalDate.now());
        Instant now = Instant.now();
        
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityEvent(null, 1, 1, now));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilityEvent(key, 1, 1, null));

        AvailabilityEvent event = new AvailabilityEvent(key, 100L, -1, now);
        assertEquals(key, event.key());
        assertEquals(100L, event.sequenceNumber());
        assertEquals(-1, event.seatDelta());
        assertEquals(now, event.timestamp());
    }
    
    @Test
    void valueSemantics() {
        LocalDate date = LocalDate.of(2023, 10, 27);
        FlightDateKey key1 = new FlightDateKey("FL123", date);
        FlightDateKey key2 = new FlightDateKey("FL123", date);
        
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }
}
