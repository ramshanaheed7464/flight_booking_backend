package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.DuffelBooking;
import com.example.flight_booking_backend.repository.DuffelBookingRepository;
import com.example.flight_booking_backend.service.DuffelService;
import com.example.flight_booking_backend.service.EmailService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/duffel")
public class DuffelController {

    private final DuffelService duffelService;
    private final EmailService emailService;
    private final DuffelBookingRepository duffelBookingRepository;
    private final ObjectMapper objectMapper;

    public DuffelController(DuffelService duffelService, EmailService emailService,
                            DuffelBookingRepository duffelBookingRepository, ObjectMapper objectMapper) {
        this.duffelService = duffelService;
        this.emailService = emailService;
        this.duffelBookingRepository = duffelBookingRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Search for live flights via Duffel.
     *
     * GET /api/duffel/flights
     *   ?origin=LHR&destination=JFK&departureDate=2025-12-01
     *   &adults=1               (default 1)
     *   &returnDate=2025-12-15  (optional — omit for one-way)
     *   &cabinClass=economy     (optional: economy | premium_economy | business | first)
     *
     * Public endpoint. Response: raw Duffel JSON; offers are in response.data.offers[].
     */
    @GetMapping("/flights")
    public ResponseEntity<?> searchFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam String departureDate,
            @RequestParam(defaultValue = "1") int adults,
            @RequestParam(required = false) String returnDate,
            @RequestParam(required = false) String cabinClass) {

        if (origin.isBlank() || destination.isBlank() || departureDate.isBlank()) {
            return ResponseEntity.badRequest().body("origin, destination, and departureDate are required");
        }
        if (adults < 1) {
            return ResponseEntity.badRequest().body("adults must be at least 1");
        }

        try {
            String result = duffelService.searchFlights(
                    origin, destination, departureDate, adults, returnDate, cabinClass);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Confirm the latest price for a selected offer before booking.
     *
     * GET /api/duffel/offers/{offerId}
     *
     * Requires authentication. Pass the offer ID from the search response
     * (response.data.offers[n].id). Returns the refreshed offer with live pricing.
     */
    @GetMapping("/offers/{offerId}")
    public ResponseEntity<?> getOffer(@PathVariable String offerId) {

        try {
            String result = duffelService.getOffer(offerId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Create a Duffel order (book a flight).
     *
     * POST /api/duffel/bookings
     * Requires authentication — the caller must supply a valid JWT.
     *
     * Body:
     * {
     *   "offerId": "off_00...",
     *   "passengers": [
     *     {
     *       "firstName":      "Ali",           // or "given_name"
     *       "lastName":       "Khan",          // or "family_name"
     *       "email":          "ali@example.com",
     *       "phone":          "+923001234567", // or "phone_number"
     *       "dateOfBirth":    "1990-01-15",    // or "born_on"  — YYYY-MM-DD
     *       "gender":         "M",             // M / F
     *       "title":          "mr",            // mr / ms / mrs / miss / dr
     *       "passportNumber": "AB1234567",     // optional
     *       "expiryDate":     "2030-06-01",    // passport expiry — optional
     *       "nationality":    "PK"             // ISO-2 issuing country — optional
     *     }
     *   ]
     * }
     */
    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return ResponseEntity.status(401).body("Authentication required. Please log in to book a flight.");
        }

        String offerId = (String) body.get("offerId");
        if (offerId == null || offerId.isBlank()) {
            return ResponseEntity.badRequest().body("offerId is required");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> passengers = (List<Map<String, Object>>) body.get("passengers");
        if (passengers == null || passengers.isEmpty()) {
            return ResponseEntity.badRequest().body("passengers are required");
        }

        try {
            String result = duffelService.createOrder(offerId, passengers);

            String userEmail = jwt.getClaimAsString("email");

            saveDuffelBooking(result, userEmail, passengers);

            // Send confirmation to each passenger's email from the form
            for (Map<String, Object> p : passengers) {
                String passengerEmail = p.containsKey("email") ? p.get("email").toString() : null;
                String passengerName  = p.containsKey("firstName") ? p.get("firstName").toString()
                        : p.containsKey("given_name") ? p.get("given_name").toString() : "Traveller";
                String toEmail = (passengerEmail != null && !passengerEmail.isBlank())
                        ? passengerEmail : userEmail;
                emailService.sendDuffelBookingConfirmation(toEmail, passengerName, result);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Validate a passenger's passport before booking.
     *
     * POST /api/duffel/validate/passport
     * Body (all strings, dates in YYYY-MM-DD):
     * {
     *   "passportNumber": "AB1234567",  // required
     *   "nationality":    "PK",         // required — ISO 3166-1 alpha-2
     *   "expiryDate":     "2030-06-01", // required
     *   "dateOfBirth":    "1990-01-15", // required
     *   "travelDate":     "2025-12-01", // optional — enables 6-month rule check
     *   "gender":         "M",          // optional — M / F / MALE / FEMALE
     *   "firstName":      "Ali",        // optional — warns if missing
     *   "lastName":       "Khan"        // optional — warns if missing
     * }
     *
     * Returns:
     *   200 { valid: true,  errors: [], warnings: [...] }
     *   400 { valid: false, errors: [...], warnings: [...] }
     */
    @PostMapping("/validate/passport")
    public ResponseEntity<?> validatePassport(@RequestBody Map<String, String> body) {

        Map<String, Object> result = duffelService.validatePassport(body);
        boolean valid = Boolean.TRUE.equals(result.get("valid"));
        return valid
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    private void saveDuffelBooking(String orderJson, String userEmail,
                                   List<Map<String, Object>> passengers) {
        try {
            JsonNode order = objectMapper.readTree(orderJson).path("data");
            JsonNode firstSegment = order.path("slices").get(0).path("segments").get(0);
            JsonNode lastSlice = order.path("slices").get(order.path("slices").size() - 1);
            JsonNode lastSegment = lastSlice.path("segments")
                    .get(lastSlice.path("segments").size() - 1);

            DuffelBooking booking = new DuffelBooking();
            booking.setBookingReference(order.path("booking_reference").asString(""));
            booking.setOrigin(firstSegment.path("origin").path("iata_code").asString(""));
            booking.setDestination(lastSegment.path("destination").path("iata_code").asString(""));
            booking.setDepartureAt(firstSegment.path("departing_at").asString(""));
            booking.setCarrier(firstSegment.path("operating_carrier").path("name").asString(""));
            booking.setFlightNumber(firstSegment.path("operating_carrier_flight_number").asString(""));
            booking.setTotalAmount(order.path("total_amount").asString(""));
            booking.setCurrency(order.path("total_currency").asString(""));
            booking.setUserEmail(userEmail);
            booking.setCreatedAt(LocalDateTime.now());
            booking.setPassengerDetailsJson(objectMapper.writeValueAsString(passengers));

            duffelBookingRepository.save(booking);
        } catch (Exception e) {
            System.err.println("Failed to save Duffel booking to DB: " + e.getMessage());
        }
    }
}
