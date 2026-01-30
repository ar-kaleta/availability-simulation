package com.example.availability.simulator.availability;

public record Availability(FlightDateKey key, int availableSeats) {
    public Availability {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (availableSeats < 0) {
            throw new IllegalArgumentException("Available seats cannot be negative");
        }
    }
}
