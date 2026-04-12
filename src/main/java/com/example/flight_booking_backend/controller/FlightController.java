package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.Flight;
import com.example.flight_booking_backend.repository.FlightRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightRepository flightRepository;

    public FlightController(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @GetMapping
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    @PostMapping("/add")
    public ResponseEntity<?> addFlight(
            @RequestBody Flight flight,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied");
        try {
            flightRepository.save(flight);
            return ResponseEntity.ok("Flight added successfully");
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body("Flight number already exists.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFlight(
            @PathVariable Long id,
            @RequestBody Flight updated,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied");

        Flight flight = flightRepository.findById(id).orElse(null);
        if (flight == null)
            return ResponseEntity.badRequest().body("Flight not found");

        flight.setFlightNumber(updated.getFlightNumber());
        flight.setSource(updated.getSource());
        flight.setDestination(updated.getDestination());
        flight.setDepartureTime(updated.getDepartureTime());
        flight.setArrivalTime(updated.getArrivalTime());
        flight.setSeatsAvailable(updated.getSeatsAvailable());
        flight.setPrice(updated.getPrice());
        flight.setMeals(updated.getMeals());
        flight.setAirline(updated.getAirline());
        flight.setDuration(updated.getDuration());
        flight.setBaggageAllowance(updated.getBaggageAllowance());
        flight.setEntertainmentAvailability(updated.getEntertainmentAvailability());
        flight.setInFlightEntertainment(updated.getInFlightEntertainment());
        flight.setWifiAvailability(updated.getWifiAvailability());
        flight.setSeatType(updated.getSeatType());
        flight.setMealsIncluded(updated.getMealsIncluded());
        flight.setRefundable(updated.isRefundable());
        flightRepository.save(flight);
        return ResponseEntity.ok("Flight updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFlight(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied");

        if (!flightRepository.existsById(id))
            return ResponseEntity.badRequest().body("Flight not found");

        flightRepository.deleteById(id);
        return ResponseEntity.ok("Flight deleted successfully");
    }

    private boolean isAdmin(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null)
                return false;
            List<?> roles = (List<?>) realmAccess.get("roles");
            return roles != null && (roles.contains("ADMIN") || roles.contains("admin"));
        } catch (Exception e) {
            return false;
        }
    }
}