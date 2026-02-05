package com.example.availability.simulator.kafka;

import com.example.availability.simulator.availability.AvailabilityCache;
import com.example.availability.simulator.availability.AvailabilityEvent;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class AvailabilityConsumer {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityConsumer.class);

    private final FlightRepository flightRepository;
    private final AvailabilityCache availabilityCache;
    private final ObjectMapper objectMapper;

    public AvailabilityConsumer(FlightRepository flightRepository, AvailabilityCache availabilityCache, ObjectMapper objectMapper) {
        this.flightRepository = flightRepository;
        this.availabilityCache = availabilityCache;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "availability-updates", groupId = "availability-simulator-group")
    @Transactional
    public void handleAvailabilityEvent(String message) {
        try {
            log.debug("Received availability update: {}", message);
            AvailabilityEvent event = objectMapper.readValue(message, AvailabilityEvent.class);

            // 1. Update Database (System of Record)
            updateDatabase(event);

            // 2. Update Cache (Near Real-Time)
            availabilityCache.updateAvailability(event);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse availability event: {}", message, e);
            // In a real system, send to DLQ
        } catch (Exception e) {
            log.error("Error processing availability event", e);
            throw e; // Trigger retry/DLQ
        }
    }

    private void updateDatabase(AvailabilityEvent event) {
        Optional<Flight> flightOpt = flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
                event.key().flightNumber(),
                event.key().origin(),
                event.key().destination(),
                event.key().departureDateTime()
        );

        if (flightOpt.isPresent()) {
            Flight flight = flightOpt.get();
            flight.setAvailability(event.availabilityString());
            flightRepository.save(flight);
            log.info("Updated flight {} availability to {}", event.key().flightNumber(), event.availabilityString());
        } else {
            log.warn("Flight not found for update: {}", event.key());
            // Optionally create it, or ignore. GDS usually updates existing inventory.
        }
    }
}
