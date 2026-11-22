package com.example.availability.simulator.simulator;

import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrafficGeneratorTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private RestTemplate restTemplate;

    private TrafficGeneratorProperties properties;
    private TrafficGenerator trafficGenerator;

    private static final String ORIGIN = "JFK";
    private static final String DESTINATION = "LHR";
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2025, 6, 15, 10, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2025, 6, 15, 13, 0);

    @BeforeEach
    void setUp() {
        properties = new TrafficGeneratorProperties();
        properties.setEnabled(true);
        properties.setRequestCount(3);
        trafficGenerator = new TrafficGenerator(flightRepository, restTemplate, properties);
        // Manually set base URL since ApplicationReadyEvent won't fire
        reflectSetBaseUrl("http://localhost:8080");
    }

    private void reflectSetBaseUrl(String baseUrl) {
        try {
            var field = TrafficGenerator.class.getDeclaredField("baseUrl");
            field.setAccessible(true);
            field.set(trafficGenerator, baseUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateTraffic_sendsRequestsForRandomFlights() throws Exception {
        // Given: 3 flights in the database
        Flight flight1 = createFlight(100, "F9 J5 Y3");
        Flight flight2 = createFlight(101, "F8 J4 Y2");
        Flight flight3 = createFlight(102, "F7 J3 Y1");

        when(flightRepository.findRandomFlights(3)).thenReturn(List.of(flight1, flight2, flight3));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("F9 J5 Y3"));

        // When: generate traffic
        trafficGenerator.generateTraffic();

        // Then: 3 requests should be sent
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(3)).getForEntity(urlCaptor.capture(), eq(String.class));

        List<String> urls = urlCaptor.getAllValues();
        assertThat(urls).hasSize(3);
        assertThat(urls.get(0)).contains("flightNumber=100");
        assertThat(urls.get(1)).contains("flightNumber=101");
        assertThat(urls.get(2)).contains("flightNumber=102");
    }

    @Test
    void generateTraffic_skipsWhenDisabled() {
        // Given: traffic generator is disabled
        properties.setEnabled(false);

        // When: generate traffic
        trafficGenerator.generateTraffic();

        // Then: no requests should be sent
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void generateTraffic_handlesEmptyFlightList() {
        // Given: no flights in database
        when(flightRepository.findRandomFlights(3)).thenReturn(List.of());

        // When: generate traffic
        trafficGenerator.generateTraffic();

        // Then: no REST calls
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void generateTraffic_handlesRequestFailure_gracefully() throws Exception {
        // Given: flights that cause errors
        properties.setRequestCount(1);
        Flight flight = createFlight(200, "F9 J5 Y3");
        when(flightRepository.findRandomFlights(1)).thenReturn(List.of(flight));
        doThrow(new RuntimeException("Connection refused"))
                .when(restTemplate).getForEntity(anyString(), eq(String.class));

        // When: generate traffic (should not throw)
        trafficGenerator.generateTraffic();

        // Then: no exception propagated
    }

    private Flight createFlight(int flightNumber, String availability) {
        Flight flight = new Flight();
        flight.setFlightNumber(flightNumber);
        flight.setOrigin(ORIGIN);
        flight.setDestination(DESTINATION);
        flight.setDepartureDateTime(DEPARTURE);
        flight.setArrivalDateTime(ARRIVAL);
        flight.setAvailability(availability);
        return flight;
    }
}
