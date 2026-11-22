package com.example.availability.simulator;

import com.example.availability.simulator.simulator.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class Application {
  static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
