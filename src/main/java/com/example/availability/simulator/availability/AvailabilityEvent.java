package com.example.availability.simulator.availability;

import java.time.Instant;

public record AvailabilityEvent(
    FlightDateKey key,
    long sequenceNumber,
    String availabilityString,
    Instant timestamp
) {
    public AvailabilityEvent {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (availabilityString == null) {
            throw new IllegalArgumentException("Availability string cannot be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }
}
