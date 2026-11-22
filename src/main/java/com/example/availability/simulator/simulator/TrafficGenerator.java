package com.example.availability.simulator.simulator;

import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrafficGenerator {

    private static final Logger log = LoggerFactory.getLogger(TrafficGenerator.class);

    private final FlightRepository flightRepository;
    private final RestTemplate restTemplate;
    private final TrafficGeneratorProperties properties;

    @Value("${server.port:8080}")
    private int serverPort;

    private String baseUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        this.baseUrl = "http://localhost:" + serverPort;
        log.info("TrafficGenerator initialized with base URL: {}", baseUrl);
    }

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final AtomicLong requestStartTime = new AtomicLong(System.currentTimeMillis());

    public TrafficGenerator(FlightRepository flightRepository,
                            RestTemplate restTemplate,
                            TrafficGeneratorProperties properties) {
        this.flightRepository = flightRepository;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${simulator.traffic.interval-ms:1000}", fixedDelayString = "${simulator.traffic.interval-ms:1000}")
    public void generateTraffic() {
        if (!properties.isEnabled()) {
            return;
        }

        int requestCount = properties.getRequestCount();
        List<Flight> flights = flightRepository.findRandomFlights(requestCount);

        if (flights.isEmpty()) {
            log.debug("No flights available for traffic generation");
            return;
        }

        int batchRequests = 0;
        long batchLatency = 0;

        for (Flight flight : flights) {
            if (baseUrl == null) {
                continue;
            }
            String url = String.format(
                    "%s/availability?flightNumber=%d&origin=%s&destination=%s&departureDateTime=%s&arrivalDateTime=%s",
                    baseUrl,
                    flight.getFlightNumber(),
                    flight.getOrigin(),
                    flight.getDestination(),
                    flight.getDepartureDateTime(),
                    flight.getArrivalDateTime()
            );

            long start = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                long latency = System.currentTimeMillis() - start;

                batchRequests++;
                batchLatency += latency;

                totalRequests.incrementAndGet();
                totalLatencyMs.addAndGet(latency);

                // Update min/max atomically
                updateMin(latency);
                updateMax(latency);

            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.warn("Request to /availability failed for flight {}: {} ({}ms)",
                        flight.getFlightNumber(), e.getMessage(), latency);
            }
        }

        // Log batch stats
        if (batchRequests > 0) {
            long avgLatency = batchLatency / batchRequests;
            long elapsedSeconds = (System.currentTimeMillis() - requestStartTime.get()) / 1000;
            double requestsPerSecond = elapsedSeconds > 0 ? totalRequests.get() / (double) elapsedSeconds : 0;

            log.info("Traffic stats — batch: {} requests, avg latency: {}ms | total: {} requests, " +
                            "avg: {}ms, min: {}ms, max: {}ms, throughput: {:.1f} req/s",
                    batchRequests, avgLatency,
                    totalRequests.get(),
                    totalRequests.get() > 0 ? totalLatencyMs.get() / totalRequests.get() : 0,
                    minLatencyMs.get() == Long.MAX_VALUE ? 0 : minLatencyMs.get(),
                    maxLatencyMs.get(),
                    requestsPerSecond);
        }
    }

    private void updateMin(long latency) {
        long current;
        do {
            current = minLatencyMs.get();
            if (latency >= current) return;
        } while (!minLatencyMs.compareAndSet(current, latency));
    }

    private void updateMax(long latency) {
        long current;
        do {
            current = maxLatencyMs.get();
            if (latency <= current) return;
        } while (!maxLatencyMs.compareAndSet(current, latency));
    }
}
