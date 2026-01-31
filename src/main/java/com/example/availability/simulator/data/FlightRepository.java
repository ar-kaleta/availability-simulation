package com.example.availability.simulator.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Integer> {
    
    Optional<Flight> findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
            Integer flightNumber,
            String origin,
            String destination,
            LocalDateTime departureDateTime
    );
}
