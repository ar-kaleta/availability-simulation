package com.example.availability.simulator.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final FlightRepository flightRepository;
    private final Random random = new Random();

    public DatabaseSeeder(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Override
    public void run(String... args) {
        if (flightRepository.count() > 0) {
            System.out.println("Database already seeded.");
            return;
        }

        System.out.println("Seeding database with mock flights...");
        List<Flight> flights = new ArrayList<>();
        String[] origins = {"JFK", "LHR", "CDG", "FRA", "DXB", "SIN", "HND", "SYD"};
        String[] destinations = {"LAX", "SFO", "MIA", "ORD", "BOS", "YYZ", "YVR", "MEX"};

        for (int i = 0; i < 1000; i++) { // Generating 1000 flights for demo
            String origin = origins[random.nextInt(origins.length)];
            String destination = destinations[random.nextInt(destinations.length)];

            // Ensure origin != destination
            while (origin.equals(destination)) {
                destination = destinations[random.nextInt(destinations.length)];
            }

            LocalDateTime departure = LocalDateTime.now().plusDays(random.nextInt(30)).plusHours(random.nextInt(24));
            LocalDateTime arrival = departure.plusHours(2 + random.nextInt(10));

            Flight flight = new Flight();
            flight.setFlightNumber(100 + random.nextInt(900)); // 100-999
            flight.setOrigin(origin);
            flight.setDestination(destination);
            flight.setDepartureDateTime(departure);
            flight.setArrivalDateTime(arrival);
            flight.setAvailability(generateRandomAvailability());

            flights.add(flight);
        }

        flightRepository.saveAll(flights);
        System.out.println("Seeding complete. Added " + flights.size() + " flights.");
    }

    private String generateRandomAvailability() {
        // Example: F9 J9 Y9, or F2 J0 Y5
        return String.format("F%d J%d Y%d",
                random.nextInt(10),
                random.nextInt(10),
                random.nextInt(10));
    }
}
