package com.example.flight_booking_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DuffelService {

    private static final String BASE_URL = "https://api.duffel.com";
    private static final String DUFFEL_VERSION = "v2";

    @Value("${duffel.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Flight Search ────────────────────────────────────────────────────────

    /**
     * Create a Duffel offer request and return all offers inline.
     *
     * @param origin        IATA airport/city code (e.g. "LHR")
     * @param destination   IATA airport/city code (e.g. "JFK")
     * @param departureDate ISO date (e.g. "2025-12-01")
     * @param adults        number of adult passengers (≥ 1)
     * @param returnDate    ISO date for return leg, or null for one-way
     * @param cabinClass    economy | premium_economy | business | first (or null)
     * @return raw Duffel JSON — response.data.offers[] contains all offers
     */
    public String searchFlights(String origin, String destination, String departureDate,
            int adults, String returnDate, String cabinClass) throws Exception {
        String body = buildOfferRequestBody(origin, destination, departureDate, adults, returnDate, cabinClass);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/air/offer_requests?return_offers=true"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Duffel-Version", DUFFEL_VERSION)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Flight search failed: " + response.body());
        }
        return response.body();
    }

    // ── Offer Price Confirmation ─────────────────────────────────────────────

    /**
     * Fetch the latest price for a specific offer before booking.
     * The client passes back the offer ID obtained from searchFlights.
     *
     * @param offerId Duffel offer ID (e.g. "off_00...")
     * @return raw Duffel JSON with updated pricing
     */
    public String getOffer(String offerId) throws Exception {
        String encoded = URLEncoder.encode(offerId.trim(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/air/offers/" + encoded))
                .header("Authorization", "Bearer " + apiKey)
                .header("Duffel-Version", DUFFEL_VERSION)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Offer not found or expired: " + response.body());
        }
        return response.body();
    }

    // ── Order Creation (Booking) ─────────────────────────────────────────────

    /**
     * Create a Duffel order for a previously searched offer.
     *
     * The method:
     * 1. Re-fetches the offer to get live passenger IDs and pricing.
     * 2. Validates that each passenger's date of birth matches the offer's
     * passenger type (adult / child / infant_without_seat). If not, a
     * human-readable exception is thrown before any request is sent.
     * 3. Submits the order to Duffel and returns the raw JSON response.
     *
     * @param offerId    Duffel offer ID from searchFlights
     * @param passengers list of passenger detail maps (accepts both frontend-style
     *                   keys like "firstName"/"dateOfBirth" and Duffel-style keys)
     * @return raw Duffel order JSON — order reference is at data.booking_reference
     */
    public String createOrder(String offerId, List<Map<String, Object>> passengers) throws Exception {
        // Fetch offer to get passenger IDs and current pricing
        String offerJson = getOffer(offerId);
        var offerRoot = objectMapper.readTree(offerJson);
        var offerData = offerRoot.path("data");
        var offerPassengers = offerData.path("passengers");
        String totalAmount = offerData.path("total_amount").asString();
        String totalCurrency = offerData.path("total_currency").asString();

        List<Map<String, Object>> duffelPassengers = new ArrayList<>();
        for (int i = 0; i < passengers.size(); i++) {
            Map<String, Object> p = passengers.get(i);
            Map<String, Object> dp = new LinkedHashMap<>();

            if (i < offerPassengers.size()) {
                String passengerId = offerPassengers.get(i).path("id").asString();
                String offerType = offerPassengers.get(i).path("type").asString("adult");
                dp.put("id", passengerId);

                // Validate DOB matches the offer's passenger type — give a friendly error
                // instead of letting the cryptic Duffel error bubble up to the user.
                String bornOn = getStr(p, "born_on", "dateOfBirth");
                if (bornOn != null && !bornOn.isBlank()) {
                    String derivedType = derivePassengerType(bornOn);
                    if (!derivedType.equals(offerType)) {
                        String firstName = getStr(p, "given_name", "firstName");
                        String label = firstName != null ? firstName : "Passenger " + (i + 1);
                        throw new IllegalArgumentException(
                                label + " (" + derivedType + ") does not match the expected type '"
                                        + offerType + "' for this offer. "
                                        + "Please search again selecting the correct passenger category.");
                    }
                }
            }

            dp.put("given_name", getStr(p, "given_name", "firstName"));
            dp.put("family_name", getStr(p, "family_name", "lastName"));
            dp.put("email", getStr(p, "email", "email"));
            dp.put("phone_number", getStr(p, "phone_number", "phone"));
            dp.put("born_on", getStr(p, "born_on", "dateOfBirth"));

            String gender = getStr(p, "gender", "gender");
            dp.put("gender", gender != null && gender.toUpperCase().startsWith("F") ? "f" : "m");

            String title = getStr(p, "title", "title");
            dp.put("title", title != null ? title.toLowerCase() : "mr");

            // Identity document (passport)
            String passportNumber = getStr(p, "passport_number", "passportNumber");
            if (passportNumber != null && !passportNumber.isBlank()) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("type", "passport");
                doc.put("number", passportNumber.toUpperCase().trim());
                doc.put("expires_on", getStr(p, "expires_on", "expiryDate"));
                doc.put("issuing_country_code", getStr(p, "issuing_country_code", "nationality"));
                dp.put("identity_documents", List.of(doc));
            }

            duffelPassengers.add(dp);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("selected_offers", List.of(offerId));
        data.put("passengers", duffelPassengers);
        data.put("payments", List.of(
                Map.of("type", "balance", "amount", totalAmount, "currency", totalCurrency)));
        data.put("type", "instant");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("data", data);
        String body = objectMapper.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/air/orders"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Duffel-Version", DUFFEL_VERSION)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            // Extract the first Duffel error title+message for a cleaner response
            try {
                var errRoot = objectMapper.readTree(response.body());
                var errors = errRoot.path("errors");
                if (errors.isArray() && !errors.isEmpty()) {
                    String title = errors.get(0).path("title").asString("");
                    String message = errors.get(0).path("message").asString("");
                    if (!title.isBlank() || !message.isBlank()) {
                        throw new IllegalArgumentException(title + (message.isBlank() ? "" : ": " + message));
                    }
                }
            } catch (IllegalArgumentException rethrow) {
                throw rethrow;
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("Booking failed: " + response.body());
        }
        return response.body();
    }

    // ── Passport Validation ──────────────────────────────────────────────────

    /**
     * Validate passenger passport details locally (no external API call).
     *
     * Expected keys in {@code data} (all strings, dates in YYYY-MM-DD):
     * passportNumber, nationality (ISO-2), expiryDate, dateOfBirth,
     * travelDate (optional), gender (M/F, optional),
     * firstName (optional), lastName (optional)
     *
     * @return map with: valid (boolean), errors (List), warnings (List)
     */
    public Map<String, Object> validatePassport(Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String passportNumber = data.get("passportNumber");
        String nationality = data.get("nationality");
        String expiryDateStr = data.get("expiryDate");
        String dobStr = data.get("dateOfBirth");
        String travelDateStr = data.get("travelDate");
        String gender = data.get("gender");
        String firstName = data.get("firstName");
        String lastName = data.get("lastName");

        // Passport number: 6–12 alphanumeric chars
        if (passportNumber == null || passportNumber.isBlank()) {
            errors.add("Passport number is required");
        } else if (!Pattern.matches("[A-Z0-9]{6,12}", passportNumber.toUpperCase().trim())) {
            errors.add("Passport number must be 6–12 alphanumeric characters (no spaces or special characters)");
        }

        // Nationality: ISO 3166-1 alpha-2
        if (nationality == null || nationality.isBlank()) {
            errors.add("Nationality is required");
        } else if (!Pattern.matches("[A-Za-z]{2}", nationality.trim())) {
            errors.add("Nationality must be a 2-letter ISO country code (e.g. PK, US, GB)");
        }

        // Expiry date
        LocalDate expiry = null;
        if (expiryDateStr == null || expiryDateStr.isBlank()) {
            errors.add("Passport expiry date is required");
        } else {
            try {
                expiry = LocalDate.parse(expiryDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate today = LocalDate.now();
                if (expiry.isBefore(today)) {
                    errors.add("Passport has already expired on " + expiry);
                } else {
                    long monthsLeft = ChronoUnit.MONTHS.between(today, expiry);
                    if (monthsLeft < 6) {
                        warnings.add("Passport expires in " + monthsLeft
                                + " month(s) — most countries require at least 6 months validity");
                    }
                    if (travelDateStr != null && !travelDateStr.isBlank()) {
                        try {
                            LocalDate travel = LocalDate.parse(travelDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                            if (ChronoUnit.MONTHS.between(travel, expiry) < 6) {
                                warnings.add("Passport expires within 6 months of travel date ("
                                        + travel + ") — entry may be refused");
                            }
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                }
            } catch (DateTimeParseException e) {
                errors.add("Expiry date must be in YYYY-MM-DD format");
            }
        }

        // Date of birth
        if (dobStr == null || dobStr.isBlank()) {
            errors.add("Date of birth is required");
        } else {
            try {
                LocalDate dob = LocalDate.parse(dobStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                if (dob.isAfter(LocalDate.now())) {
                    errors.add("Date of birth cannot be in the future");
                }
                if (expiry != null && dob.isAfter(expiry)) {
                    errors.add("Date of birth cannot be after passport expiry date");
                }
            } catch (DateTimeParseException e) {
                errors.add("Date of birth must be in YYYY-MM-DD format");
            }
        }

        // Gender (optional)
        if (gender != null && !gender.isBlank()) {
            String g = gender.toUpperCase().trim();
            if (!g.equals("M") && !g.equals("F") && !g.equals("MALE") && !g.equals("FEMALE")) {
                errors.add("Gender must be M, F, MALE, or FEMALE");
            }
        }

        // Name warnings
        if (firstName == null || firstName.isBlank())
            warnings.add("First name is missing — required for booking");
        if (lastName == null || lastName.isBlank())
            warnings.add("Last name is missing — required for booking");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        return result;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Derive Duffel passenger type from date of birth. */
    private String derivePassengerType(String bornOn) {
        try {
            LocalDate dob = LocalDate.parse(bornOn.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            int age = Period.between(dob, LocalDate.now()).getYears();
            if (age >= 18)
                return "adult";
            if (age >= 2)
                return "child";
            return "infant_without_seat";
        } catch (Exception e) {
            return "adult";
        }
    }

    /** Return the first non-blank value found under any of the given keys. */
    private String getStr(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank())
                return val.toString();
        }
        return null;
    }

    private String buildOfferRequestBody(String origin, String destination, String departureDate,
            int adults, String returnDate, String cabinClass) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();

        // Slices (one per flight leg)
        List<Map<String, String>> slices = new ArrayList<>();

        Map<String, String> outbound = new LinkedHashMap<>();
        outbound.put("origin", origin.toUpperCase().trim());
        outbound.put("destination", destination.toUpperCase().trim());
        outbound.put("departure_date", departureDate.trim());
        slices.add(outbound);

        if (returnDate != null && !returnDate.isBlank()) {
            Map<String, String> inbound = new LinkedHashMap<>();
            inbound.put("origin", destination.toUpperCase().trim());
            inbound.put("destination", origin.toUpperCase().trim());
            inbound.put("departure_date", returnDate.trim());
            slices.add(inbound);
        }
        data.put("slices", slices);

        // Passengers
        List<Map<String, String>> passengers = new ArrayList<>();
        for (int i = 0; i < adults; i++) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("type", "adult");
            passengers.add(p);
        }
        data.put("passengers", passengers);

        // Cabin class (omit if not specified — Duffel returns all classes)
        if (cabinClass != null && !cabinClass.isBlank()) {
            data.put("cabin_class", cabinClass.toLowerCase().trim());
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("data", data);
        return objectMapper.writeValueAsString(root);
    }
}
