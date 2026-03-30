package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.Booking;
import com.example.flight_booking_backend.model.BookingStatus;
import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.BookingRepository;
import com.example.flight_booking_backend.repository.FlightRepository;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.security.JwtUtil;
import com.example.flight_booking_backend.service.EmailService;

import tools.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
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
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public BookingController(BookingRepository bookingRepository, UserRepository userRepository,
            FlightRepository flightRepository, JwtUtil jwtUtil, ObjectMapper objectMapper,
            EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.flightRepository = flightRepository;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    // ── User endpoints ────────────────────────────────────────────

    // POST /api/bookings
    @PostMapping
    public ResponseEntity<?> bookFlight(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {

        String email = extractEmail(authHeader);
        if (email == null)
            return ResponseEntity.status(401).body("Unauthorized");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("User not found");

        Long flightId = Long.valueOf(body.get("flightId").toString());
        String tripType = body.getOrDefault("tripType", "ONE_WAY").toString();
        int passengers = Integer.parseInt(body.getOrDefault("passengers", "1").toString());

        // Serialize passenger details to JSON string
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

        // Save outbound booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setFlight(flight);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookingTime(LocalDateTime.now());
        booking.setTripType(tripType);
        booking.setPassengers(passengers);
        booking.setPassengerDetails(passengerDetailsJson);
        Booking savedBooking = bookingRepository.save(booking);

        // Round trip — save return booking, then send ONE combined email
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

    // GET /api/bookings — current user's bookings
    @GetMapping
    public ResponseEntity<?> getMyBookings(@RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null)
            return ResponseEntity.status(401).body("Unauthorized");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("User not found");
        return ResponseEntity.ok(bookingRepository.findByUserId(user.getId()));
    }

    // PUT /api/bookings/{id}/cancel — user cancels their own booking
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        String email = extractEmail(authHeader);
        if (email == null)
            return ResponseEntity.status(401).body("Unauthorized");
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

    // ── Admin endpoints ───────────────────────────────────────────

    // GET /api/bookings/all — all bookings (admin only)
    @GetMapping("/all")
    public ResponseEntity<?> getAllBookings(@RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");
        List<Booking> all = bookingRepository.findAll();
        return ResponseEntity.ok(all);
    }

    // PUT /api/bookings/{id}/status — update booking status (admin only)
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
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

            // Restore seats if admin cancels an active booking
            if (newStatus == BookingStatus.CANCELLED
                    && booking.getStatus() != BookingStatus.CANCELLED) {
                var flight = booking.getFlight();
                flight.setSeatsAvailable(flight.getSeatsAvailable() + booking.getPassengers());
                flightRepository.save(flight);
            }

            bookingRepository.save(booking);
            return ResponseEntity.ok("Booking status updated to " + newStatus);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + statusStr);
        }
    }

    // DELETE /api/bookings/{id} — delete a booking (admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBooking(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null)
            return ResponseEntity.badRequest().body("Booking not found");

        // Restore seats if booking was active
        if (booking.getStatus() == BookingStatus.BOOKED) {
            var flight = booking.getFlight();
            flight.setSeatsAvailable(flight.getSeatsAvailable() + booking.getPassengers());
            flightRepository.save(flight);
        }

        bookingRepository.deleteById(id);
        return ResponseEntity.ok("Booking deleted");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            return jwtUtil.extractClaims(authHeader.substring(7)).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return false;
        try {
            String role = jwtUtil.extractRole(authHeader.substring(7));
            return "ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }
}