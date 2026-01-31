package com.example.availability.simulator.availability;

import java.time.LocalDate;

public record FlightDateKey(
    String flightNumber,
    LocalDate date,
    String origin,
    String destination
) {
    public FlightDateKey {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("Flight number cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Origin cannot be null or empty");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination cannot be null or empty");
        }
    }
}
