package com.example.availability.simulator.service;

import com.example.availability.simulator.availability.Availability;
import com.example.availability.simulator.availability.AvailabilityCache;
import com.example.availability.simulator.availability.FlightDateKey;
import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AvailabilityService {

    private final AvailabilityCache availabilityCache;
    private final FlightRepository flightRepository;
    private final Timer dbReadTimer;

    public AvailabilityService(AvailabilityCache availabilityCache,
                               FlightRepository flightRepository,
                               MeterRegistry meterRegistry) {
        this.availabilityCache = availabilityCache;
        this.flightRepository = flightRepository;
        this.dbReadTimer = Timer.builder("availability.database.read")
                .description("Database read latency")
                .register(meterRegistry);
    }

    public Optional<Availability> getAvailability(FlightDateKey key) {
        // 1. Check Cache
        Optional<Availability> cached = availabilityCache.getAvailability(key);
        if (cached.isPresent()) {
            return cached;
        }

        // 2. Check Database (with timing)
        long start = System.nanoTime();
        Optional<Flight> flightOpt = flightRepository.findByFlightNumberAndOriginAndDestinationAndDepartureDateTime(
                key.flightNumber(),
                key.origin(),
                key.destination(),
                key.departureDateTime()
        );
        dbReadTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);

        if (flightOpt.isPresent()) {
            Flight flight = flightOpt.get();
            Availability availability = new Availability(key, flight.getAvailability());

            // 3. Populate Cache
            availabilityCache.putFromDatabase(availability);

            return Optional.of(availability);
        }

        // 4. Not found
        return Optional.empty();
    }
}
