package com.example.availability.simulator.availability;

import java.time.Instant;

public record AvailabilityEvent(
    FlightDateKey key,
    long sequenceNumber,
    int seatDelta,
    Instant timestamp
) {
    public AvailabilityEvent {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }
}
