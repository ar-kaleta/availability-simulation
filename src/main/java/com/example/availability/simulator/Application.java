package com.example.availability.simulator;

import com.example.availability.simulator.data.Flight;
import com.example.availability.simulator.data.FlightRepository;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  CommandLineRunner commandLineRunner(FlightRepository flightRepository) {
    return args -> {
      if (flightRepository.count() == 0) {
        var flight = new Flight(
            null, 1, "KRK", "WAW", LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(2).plusHours(2), "AVAILABLE");

        flightRepository.save(flight);
      }
    };
  }
}
