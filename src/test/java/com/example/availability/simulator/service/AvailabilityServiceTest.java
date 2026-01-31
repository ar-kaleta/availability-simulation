package com.example.availability.simulator.service;

import com.example.availability.simulator.availability.Availability;
import com.example.availability.simulator.availability.AvailabilityCache;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private AvailabilityCache availabilityCache;

    @Mock
    private FlightRepository flightRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    @Test
    void getAvailability_returnsCachedValue_whenPresentInCache() {
        // Given
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        Availability cachedAvailability = new Availability(key, "F9 J9 Y9");

        when(availabilityCache.getAvailability(key)).thenReturn(Optional.of(cachedAvailability));

        // When
        Optional<Availability> result = availabilityService.getAvailability(key);

        // Then
        assertTrue(result.isPresent());
        assertEquals(cachedAvailability, result.get());
        verify(flightRepository, never()).findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(any(), any(), any(), any());
    }

    @Test
    void getAvailability_fetchesFromDbAndPopulatesCache_whenMissingInCache() {
        // Given
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);

        when(availabilityCache.getAvailability(key)).thenReturn(Optional.empty());

        Flight flight = new Flight();
        flight.setFlightNumber(123);
        flight.setOrigin("JFK");
        flight.setDestination("LHR");
        flight.setDepartureDateTime(dep);
        flight.setArrivalDateTime(arr);
        flight.setAvailability("F5 J5 Y5");

        when(flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(123, "JFK", "LHR", dep))
                .thenReturn(Optional.of(flight));

        // When
        Optional<Availability> result = availabilityService.getAvailability(key);

        // Then
        assertTrue(result.isPresent());
        assertEquals("F5 J5 Y5", result.get().availabilityString());
        verify(availabilityCache).putFromDatabase(any(Availability.class));
    }

    @Test
    void getAvailability_returnsEmpty_whenMissingInCacheAndDb() {
        // Given
        LocalDateTime dep = LocalDateTime.now();
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);

        when(availabilityCache.getAvailability(key)).thenReturn(Optional.empty());
        when(flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(123, "JFK", "LHR", dep))
                .thenReturn(Optional.empty());

        // When
        Optional<Availability> result = availabilityService.getAvailability(key);

        // Then
        assertTrue(result.isEmpty());
    }
}
