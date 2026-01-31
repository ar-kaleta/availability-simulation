package com.example.availability.simulator.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseSeederTest {

    @Mock
    private FlightRepository flightRepository;

    @InjectMocks
    private DatabaseSeeder databaseSeeder;

    @Test
    void run_seedsDatabase_whenEmpty() {
        // Given
        when(flightRepository.count()).thenReturn(0L);

        // When
        databaseSeeder.run();

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Flight>> captor = ArgumentCaptor.forClass(List.class);
        verify(flightRepository).saveAll(captor.capture());

        List<Flight> savedFlights = captor.getValue();
        assertEquals(1000, savedFlights.size());

        Flight firstFlight = savedFlights.getFirst();
        assertNotNull(firstFlight.getFlightNumber());
        assertNotNull(firstFlight.getOrigin());
        assertNotNull(firstFlight.getDestination());
        assertNotNull(firstFlight.getDepartureDateTime());
        assertNotNull(firstFlight.getArrivalDateTime());
        assertNotNull(firstFlight.getAvailability());

        // Verify availability format matches "F%d J%d Y%d"
        assertTrue(firstFlight.getAvailability().matches("F\\d+ J\\d+ Y\\d+"));
    }

    @Test
    void run_doesNotSeed_whenNotEmpty() {
        // Given
        when(flightRepository.count()).thenReturn(5L);

        // When
        databaseSeeder.run();

        // Then
        verify(flightRepository, never()).saveAll(any());
    }
}
