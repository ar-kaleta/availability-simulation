package com.example.availability.simulator.controller;

import com.example.availability.simulator.availability.Availability;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.service.AvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    @Test
    void getAvailability_returnsOk_whenFound() throws Exception {
        LocalDateTime dep = LocalDateTime.of(2023, 10, 27, 10, 0);
        LocalDateTime arr = dep.plusHours(2);
        FlightDateKey key = new FlightDateKey(123, "JFK", "LHR", dep, arr);
        Availability availability = new Availability(key, "F9 J9 Y9");

        when(availabilityService.getAvailability(any(FlightDateKey.class))).thenReturn(Optional.of(availability));

        mockMvc.perform(get("/availability")
                        .param("flightNumber", "123")
                        .param("origin", "JFK")
                        .param("destination", "LHR")
                        .param("departureDateTime", "2023-10-27T10:00:00")
                        .param("arrivalDateTime", "2023-10-27T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("F9 J9 Y9"));
    }

    @Test
    void getAvailability_returnsNotFound_whenMissing() throws Exception {
        when(availabilityService.getAvailability(any(FlightDateKey.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/availability")
                        .param("flightNumber", "999")
                        .param("origin", "JFK")
                        .param("destination", "LHR")
                        .param("departureDateTime", "2023-10-27T10:00:00")
                        .param("arrivalDateTime", "2023-10-27T12:00:00"))
                .andExpect(status().isNotFound());
    }
}
