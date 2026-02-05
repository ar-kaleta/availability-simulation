package com.example.availability.simulator.kafka;

import com.example.availability.simulator.availability.AvailabilityCache;
import com.example.availability.simulator.availability.AvailabilityEvent;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityConsumerTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private AvailabilityCache availabilityCache;

    private AvailabilityConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new AvailabilityConsumer(flightRepository, availabilityCache, objectMapper);
    }

    @Test
    void handleAvailabilityEvent_updatesDbAndCache_whenFlightExists() throws Exception {
        // Given
        LocalDateTime dep = LocalDateTime.of(2023, 10, 27, 10, 0);
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        AvailabilityEvent event = new AvailabilityEvent(key, 1L, "F5 J5 Y5", Instant.now());
        String json = objectMapper.writeValueAsString(event);

        Flight flight = new Flight();
        flight.setFlightNumber(123);

        when(flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(123, "JFK", "LHR", dep))
                .thenReturn(Optional.of(flight));

        // When
        consumer.handleAvailabilityEvent(json);

        // Then
        verify(flightRepository).save(flight);
        verify(availabilityCache).updateAvailability(any(AvailabilityEvent.class));
    }

    @Test
    void handleAvailabilityEvent_logsWarning_whenFlightNotFound() throws Exception {
        // Given
        LocalDateTime dep = LocalDateTime.of(2023, 10, 27, 10, 0);
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        AvailabilityEvent event = new AvailabilityEvent(key, 1L, "F5 J5 Y5", Instant.now());
        String json = objectMapper.writeValueAsString(event);

        when(flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(123, "JFK", "LHR", dep))
                .thenReturn(Optional.empty());

        // When
        consumer.handleAvailabilityEvent(json);

        // Then
        verify(flightRepository, never()).save(any());
        verify(availabilityCache).updateAvailability(any(AvailabilityEvent.class)); // Cache might still be updated or not? 
        // The code updates cache regardless of DB presence (Write-Through/Behind logic might differ, but here we update cache to reflect latest event).
    }
}
