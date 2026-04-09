package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.Booking;
import com.example.flight_booking_backend.model.BookingStatus;
import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.BookingRepository;
import com.example.flight_booking_backend.repository.FlightRepository;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.service.EmailService;

import tools.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final FlightRepository flightRepository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public BookingController(BookingRepository bookingRepository, UserRepository userRepository,
            FlightRepository flightRepository, ObjectMapper objectMapper,
            EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.flightRepository = flightRepository;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<?> bookFlight(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("User not found");

        Long flightId = Long.valueOf(body.get("flightId").toString());
        String tripType = body.getOrDefault("tripType", "ONE_WAY").toString();
        int passengers = Integer.parseInt(body.getOrDefault("passengers", "1").toString());

        String passengerDetailsJson = null;
        if (body.containsKey("passengerDetails")) {
            try {
                passengerDetailsJson = objectMapper.writeValueAsString(body.get("passengerDetails"));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Invalid passenger details format");
            }
        }

        var flight = flightRepository.findById(flightId).orElse(null);
        if (flight == null)
            return ResponseEntity.badRequest().body("Flight not found");
        if (flight.getSeatsAvailable() < passengers)
            return ResponseEntity.badRequest().body("Not enough seats available");

        flight.setSeatsAvailable(flight.getSeatsAvailable() - passengers);
        flightRepository.save(flight);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setFlight(flight);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookingTime(LocalDateTime.now());
        booking.setTripType(tripType);
        booking.setPassengers(passengers);
        booking.setPassengerDetails(passengerDetailsJson);
        Booking savedBooking = bookingRepository.save(booking);

        if ("ROUND_TRIP".equals(tripType) && body.containsKey("returnFlightId")) {
            Long returnFlightId = Long.valueOf(body.get("returnFlightId").toString());
            var returnFlight = flightRepository.findById(returnFlightId).orElse(null);

            if (returnFlight != null && returnFlight.getSeatsAvailable() >= passengers) {
                returnFlight.setSeatsAvailable(returnFlight.getSeatsAvailable() - passengers);
                flightRepository.save(returnFlight);

                Booking returnBooking = new Booking();
                returnBooking.setUser(user);
                returnBooking.setFlight(returnFlight);
                returnBooking.setStatus(BookingStatus.BOOKED);
                returnBooking.setBookingTime(LocalDateTime.now());
                returnBooking.setTripType("RETURN");
                returnBooking.setPassengers(passengers);
                returnBooking.setPassengerDetails(passengerDetailsJson);
                Booking savedReturnBooking = bookingRepository.save(returnBooking);

                emailService.sendRoundTripConfirmation(savedBooking, savedReturnBooking);
            }
        } else {
            emailService.sendBookingConfirmation(savedBooking);
        }

        return ResponseEntity.ok("Flight booked successfully");
    }

    @GetMapping
    public ResponseEntity<?> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("User not found");
        return ResponseEntity.ok(bookingRepository.findByUserId(user.getId()));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("User not found");

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null)
            return ResponseEntity.badRequest().body("Booking not found");
        if (!booking.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(403).body("Access denied");
        if (booking.getStatus() == BookingStatus.CANCELLED)
            return ResponseEntity.badRequest().body("Already cancelled");

        var flight = booking.getFlight();
        flight.setSeatsAvailable(flight.getSeatsAvailable() + booking.getPassengers());
        flightRepository.save(flight);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return ResponseEntity.ok("Booking cancelled");
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBookings(@AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");
        return ResponseEntity.ok(bookingRepository.findAll());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null)
            return ResponseEntity.badRequest().body("Booking not found");

        String statusStr = body.get("status");
        if (statusStr == null)
            return ResponseEntity.badRequest().body("Status is required");

        try {
            BookingStatus newStatus = BookingStatus.valueOf(statusStr.toUpperCase());
            booking.setStatus(newStatus);
            bookingRepository.save(booking);
            return ResponseEntity.ok("Booking status updated to " + newStatus);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + statusStr);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null)
            return ResponseEntity.badRequest().body("Booking not found");

        if (booking.getStatus() == BookingStatus.BOOKED) {
            var flight = booking.getFlight();
            flight.setSeatsAvailable(flight.getSeatsAvailable() + booking.getPassengers());
            flightRepository.save(flight);
        }

        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking deleted");
    }

    private boolean isAdmin(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null)
                return false;
            List<?> roles = (List<?>) realmAccess.get("roles");
            return roles != null && roles.contains("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }
}