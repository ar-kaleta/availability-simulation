package com.example.availability.simulator.availability;

public record Availability(FlightDateKey key, String availabilityString) {
    public Availability {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (availabilityString == null) {
            throw new IllegalArgumentException("Availability string cannot be null");
        }
    }
}
