package com.example.availability.simulator.integration;

import com.example.availability.simulator.availability.AvailabilityEvent;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=test-integration-group",
        "simulator.inventory.interval-ms=999999999",
        "simulator.inventory.initial-delay-ms=999999999",
        "simulator.traffic.enabled=false"
    }
)
@AutoConfigureMockMvc
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = { "listeners=PLAINTEXT://localhost:0" },
    topics = { "availability-updates" }
)
class KafkaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORIGIN = "JFK";
    private static final String DESTINATION = "LHR";
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2025, 6, 15, 10, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2025, 6, 15, 13, 0);

    private void seedFlight(int flightNumber, String availability) {
        Flight flight = new Flight();
        flight.setFlightNumber(flightNumber);
        flight.setOrigin(ORIGIN);
        flight.setDestination(DESTINATION);
        flight.setDepartureDateTime(DEPARTURE);
        flight.setArrivalDateTime(ARRIVAL);
        flight.setAvailability(availability);
        flightRepository.saveAndFlush(flight);
    }

    private void waitForConsumer() throws InterruptedException {
        Thread.sleep(2000);
    }

    @Test
    void restReturnsInitialAvailability_fromDatabase() throws Exception {
        // Each test uses a unique flight number to avoid cache collisions
        int flightNumber = 10001;
        String initialAvailability = "F9 J5 Y3";

        // Given: a flight seeded in DB
        seedFlight(flightNumber, initialAvailability);

        // When: query via REST (no Kafka events sent)
        // Then: it should return the initial availability from the database
        mockMvc.perform(get("/availability")
                        .param("flightNumber", String.valueOf(flightNumber))
                        .param("origin", ORIGIN)
                        .param("destination", DESTINATION)
                        .param("departureDateTime", DEPARTURE.toString())
                        .param("arrivalDateTime", ARRIVAL.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(initialAvailability));
    }

    @Test
    void kafkaEvent_updatesDatabaseAndRestReturnsNewAvailability() throws Exception {
        int flightNumber = 10002;
        String initialAvailability = "F9 J5 Y3";
        String updatedAvailability = "F8 J5 Y3";

        // Given: a flight seeded in DB
        seedFlight(flightNumber, initialAvailability);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        FlightDateKey key = new FlightDateKey(flightNumber, ORIGIN, DESTINATION, DEPARTURE, ARRIVAL);
        AvailabilityEvent event = new AvailabilityEvent(key, 1L, updatedAvailability, Instant.now());
        String message = objectMapper.writeValueAsString(event);

        // When: publish the event to Kafka
        kafkaTemplate.send("availability-updates", message);
        kafkaTemplate.flush();
        waitForConsumer();

        // Then: verify the database was updated
        Flight updatedFlight = flightRepository
                .findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
                        flightNumber, ORIGIN, DESTINATION, DEPARTURE)
                .orElseThrow();
        assertThat(updatedFlight.getAvailability()).isEqualTo(updatedAvailability);

        // And: verify the REST endpoint returns the updated availability
        mockMvc.perform(get("/availability")
                        .param("flightNumber", String.valueOf(flightNumber))
                        .param("origin", ORIGIN)
                        .param("destination", DESTINATION)
                        .param("departureDateTime", DEPARTURE.toString())
                        .param("arrivalDateTime", ARRIVAL.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(updatedAvailability));
    }

    @Test
    void kafkaEvent_multipleUpdates_appliesLatestSequence() throws Exception {
        int flightNumber = 10003;
        String initialAvailability = "F9 J5 Y3";

        // Given: a flight seeded in DB
        seedFlight(flightNumber, initialAvailability);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        FlightDateKey key = new FlightDateKey(flightNumber, ORIGIN, DESTINATION, DEPARTURE, ARRIVAL);

        // When: publish two sequential updates
        AvailabilityEvent event1 = new AvailabilityEvent(key, 1L, "F8 J5 Y3", Instant.now());
        kafkaTemplate.send("availability-updates", objectMapper.writeValueAsString(event1));
        kafkaTemplate.flush();

        Thread.sleep(1500); // Let first event be consumed

        AvailabilityEvent event2 = new AvailabilityEvent(key, 2L, "F8 J4 Y3", Instant.now());
        kafkaTemplate.send("availability-updates", objectMapper.writeValueAsString(event2));
        kafkaTemplate.flush();

        waitForConsumer();

        // Then: the latest availability should be applied
        Flight updatedFlight = flightRepository
                .findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
                        flightNumber, ORIGIN, DESTINATION, DEPARTURE)
                .orElseThrow();
        assertThat(updatedFlight.getAvailability()).isEqualTo("F8 J4 Y3");
    }

    @Test
    void restReturnsNotFound_forNonExistentFlight() throws Exception {
        // When: query for a flight that doesn't exist
        // Then: it should return 404
        mockMvc.perform(get("/availability")
                        .param("flightNumber", "99999")
                        .param("origin", "XXX")
                        .param("destination", "YYY")
                        .param("departureDateTime", DEPARTURE.toString())
                        .param("arrivalDateTime", ARRIVAL.toString()))
                .andExpect(status().isNotFound());
    }
}
