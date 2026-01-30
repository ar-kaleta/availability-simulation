package com.example.availability.simulator.availability;

import java.time.LocalDate;

public record FlightDateKey(String flightNumber, LocalDate date) {
    public FlightDateKey {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("Flight number cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
    }
}
