package com.example.flight_booking_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FlightBookingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightBookingBackendApplication.class, args);
	}

}