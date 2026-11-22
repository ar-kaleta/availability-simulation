package com.example.availability.simulator.simulator;

import com.example.availability.simulator.availability.AvailabilityEvent;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySimulatorTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private InventorySimulator simulator;
    private ObjectMapper objectMapper;
    private SimulatorProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        properties = new SimulatorProperties();
        properties.setBatchSize(2);
        simulator = new InventorySimulator(flightRepository, kafkaTemplate, properties, objectMapper);
    }

    @Test
    void produceEvents_publishesEventsForRandomFlights() throws Exception {
        // Given
        LocalDateTime dep = LocalDateTime.of(2023, 10, 27, 10, 0);
        LocalDateTime arr = dep.plusHours(2);

        Flight flight1 = new Flight();
        flight1.setId(1);
        flight1.setFlightNumber(100);
        flight1.setOrigin("JFK");
        flight1.setDestination("LHR");
        flight1.setDepartureDateTime(dep);
        flight1.setArrivalDateTime(arr);
        flight1.setAvailability("F9 J5 Y3");

        Flight flight2 = new Flight();
        flight2.setId(2);
        flight2.setFlightNumber(200);
        flight2.setOrigin("SFO");
        flight2.setDestination("CDG");
        flight2.setDepartureDateTime(dep);
        flight2.setArrivalDateTime(arr);
        flight2.setAvailability("F3 J3 Y3");

        List<Flight> flights = Arrays.asList(flight1, flight2);
        when(flightRepository.findRandomFlights(2)).thenReturn(flights);

        // When
        simulator.produceEvents();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(eq("availability-updates"), messageCaptor.capture());

        List<String> messages = messageCaptor.getAllValues();
        assertEquals(2, messages.size());

        // Verify first message structure
        AvailabilityEvent event1 = objectMapper.readValue(messages.get(0), AvailabilityEvent.class);
        assertEquals(100, event1.key().flightNumber());
        assertEquals("JFK", event1.key().origin());
        assertEquals("LHR", event1.key().destination());
        assertEquals(1, event1.sequenceNumber());
        // Availability should have one seat decremented from "F9 J5 Y3"
        assertTrue(isDecrementOf(event1.availabilityString(), "F9 J5 Y3"));

        // Verify second message structure
        AvailabilityEvent event2 = objectMapper.readValue(messages.get(1), AvailabilityEvent.class);
        assertEquals(200, event2.key().flightNumber());
        assertEquals(2, event2.sequenceNumber());
        assertTrue(isDecrementOf(event2.availabilityString(), "F3 J3 Y3"));
    }

    @Test
    void produceEvents_doesNothing_whenNoFlights() {
        // Given
        when(flightRepository.findRandomFlights(2)).thenReturn(List.of());

        // When
        simulator.produceEvents();

        // Then
        verify(kafkaTemplate, times(0)).send(anyString(), anyString());
    }

    @Test
    void decrementRandomSeat_decrementsOneSeat() {
        // Given
        String availability = "F9 J5 Y3";

        // When
        String result = simulator.decrementRandomSeat(availability);

        // Then
        assertTrue(isDecrementOf(result, availability));
    }

    @Test
    void decrementRandomSeat_floorsAtZero() {
        // Given
        String availability = "F0 J0 Y0";

        // When
        String result = simulator.decrementRandomSeat(availability);

        // Then
        assertEquals("F0 J0 Y0", result);
    }

    @Test
    void decrementRandomSeat_handlesSingleCabin() {
        // Given
        String availability = "Y5";

        // When
        String result = simulator.decrementRandomSeat(availability);

        // Then
        assertEquals("Y4", result);
    }

    private boolean isDecrementOf(String result, String original) {
        String[] originalParts = original.split(" ");
        String[] resultParts = result.split(" ");

        if (originalParts.length != resultParts.length) {
            return false;
        }

        int decrementedCount = 0;
        for (int i = 0; i < originalParts.length; i++) {
            int origCount = Integer.parseInt(originalParts[i].substring(1));
            int resCount = Integer.parseInt(resultParts[i].substring(1));
            if (resCount == origCount - 1) {
                decrementedCount++;
            } else if (resCount != origCount) {
                return false;
            }
        }
        return decrementedCount == 1;
    }
}
