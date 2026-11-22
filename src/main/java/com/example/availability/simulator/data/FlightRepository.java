package com.example.availability.simulator.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Integer> {
    
    Optional<Flight> findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
            Integer flightNumber,
            String origin,
            String destination,
            LocalDateTime departureDateTime
    );

    @Query(value = "SELECT * FROM flight ORDER BY RANDOM() LIMIT ?1", nativeQuery = true)
    List<Flight> findRandomFlights(int limit);
}
