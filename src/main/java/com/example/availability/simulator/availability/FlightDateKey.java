package com.example.availability.simulator.availability;

import java.time.LocalDateTime;

public record FlightDateKey(
    Integer flightNumber,
    String origin,
    String destination,
    LocalDateTime departureDateTime,
    LocalDateTime arrivalDateTime
) {
    public FlightDateKey {
        if (flightNumber == null || flightNumber <= 0) {
            throw new IllegalArgumentException("Flight number must be a positive integer");
        }
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Origin cannot be null or empty");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination cannot be null or empty");
        }
        if (departureDateTime == null) {
            throw new IllegalArgumentException("Departure date time cannot be null");
        }
        if (arrivalDateTime == null) {
            throw new IllegalArgumentException("Arrival date time cannot be null");
        }
    }
}
