package com.example.availability.simulator.controller;

import com.example.availability.simulator.availability.Availability;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public ResponseEntity<String> getAvailability(
            @RequestParam Integer flightNumber,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalDateTime
    ) {
        FlightDateKey key = new FlightDateKey(flightNumber, origin, destination, departureDateTime, arrivalDateTime);

        return availabilityService.getAvailability(key)
                .map(Availability::availabilityString)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
