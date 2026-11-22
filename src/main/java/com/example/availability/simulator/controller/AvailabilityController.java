package com.example.availability.simulator.controller;

import com.example.availability.simulator.availability.Availability;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.service.AvailabilityService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final Timer restRequestTimer;

    public AvailabilityController(AvailabilityService availabilityService, MeterRegistry meterRegistry) {
        this.availabilityService = availabilityService;
        this.restRequestTimer = Timer.builder("availability.rest.request")
                .description("REST request latency")
                .register(meterRegistry);
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

        long start = System.nanoTime();
        try {
            return availabilityService.getAvailability(key)
                    .map(Availability::availabilityString)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } finally {
            restRequestTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
