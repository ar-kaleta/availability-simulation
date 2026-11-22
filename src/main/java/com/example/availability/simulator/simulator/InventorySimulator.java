package com.example.availability.simulator.simulator;

import com.example.availability.simulator.availability.AvailabilityEvent;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InventorySimulator {

    private static final Logger log = LoggerFactory.getLogger(InventorySimulator.class);
    private static final String TOPIC = "availability-updates";

    private final FlightRepository flightRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SimulatorProperties simulatorProperties;
    private final ObjectMapper objectMapper;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final Random random = new Random();

    public InventorySimulator(FlightRepository flightRepository,
                              KafkaTemplate<String, String> kafkaTemplate,
                              SimulatorProperties simulatorProperties,
                              ObjectMapper objectMapper) {
        this.flightRepository = flightRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.simulatorProperties = simulatorProperties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelayString = "${simulator.inventory.initial-delay-ms:1000}", fixedDelayString = "${simulator.inventory.interval-ms:5000}")
    public void produceEvents() {
        int batchSize = simulatorProperties.getBatchSize();
        List<Flight> flights = flightRepository.findRandomFlights(batchSize);

        if (flights.isEmpty()) {
            log.debug("No flights found in database");
            return;
        }

        for (Flight flight : flights) {
            try {
                String newAvailability = decrementRandomSeat(flight.getAvailability());
                long sequenceNumber = sequenceCounter.incrementAndGet();

                FlightDateKey key = new FlightDateKey(
                        flight.getFlightNumber(),
                        flight.getOrigin(),
                        flight.getDestination(),
                        flight.getDepartureDateTime(),
                        flight.getArrivalDateTime()
                );

                AvailabilityEvent event = new AvailabilityEvent(
                        key,
                        sequenceNumber,
                        newAvailability,
                        Instant.now()
                );

                String message = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(TOPIC, message);

                log.info("Published availability event for flight {}: {} -> {} (seq: {})",
                        flight.getFlightNumber(), flight.getAvailability(), newAvailability, sequenceNumber);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize availability event for flight {}", flight.getId(), e);
            }
        }
    }

    String decrementRandomSeat(String availability) {
        String[] parts = availability.split(" ");
        int index = random.nextInt(parts.length);

        String part = parts[index];
        char cabin = part.charAt(0);
        int count = Integer.parseInt(part.substring(1));

        int newCount = Math.max(0, count - 1);
        parts[index] = cabin + String.valueOf(newCount);

        return String.join(" ", parts);
    }
}
