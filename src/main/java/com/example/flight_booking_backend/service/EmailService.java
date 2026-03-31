package com.example.flight_booking_backend.service;

import com.example.flight_booking_backend.model.Booking;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;

import static com.example.flight_booking_backend.email.EmailComponents.*;
import static com.example.flight_booking_backend.email.EmailPalette.*;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.from.email}")
    private String fromEmail;

    @Value("${brevo.from.name:aerolink}")
    private String fromName;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  hh:mm a");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private void sendEmail(String to, String subject, String html) {
        try {
            String jsonBody = """
                    {
                        "sender": { "name": "%s", "email": "%s" },
                        "to": [ { "email": "%s" } ],
                        "subject": "%s",
                        "htmlContent": "%s"
                    }
                    """.formatted(
                    fromName,
                    fromEmail,
                    to,
                    subject.replace("\"", "\\\""),
                    html.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "")
                            .replace("\r", ""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("Content-Type", "application/json")
                    .header("api-key", brevoApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email sent successfully to: " + to);
            } else {
                System.err.println(
                        "Failed to send email. Status: " + response.statusCode() + ", Body: " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    @Async
    public void sendVerificationCode(String toEmail, String name, String code) {
        String subject = IC_PLANE + " Verify your AeroLink account";
        String html = buildVerificationHtml(name, code);
        sendEmail(toEmail, subject, html);
        System.out.println("Verification code sent to: " + toEmail);
    }

    @Async
    public void sendResetEmail(String toEmail, String token) {
        String link = "https://flight-booking-frontend-hgml.vercel.app/reset-password?token=" + token;
        String html = "<p>Click the link to reset your password:</p><a href='" + link + "'>" + link + "</a>";
        sendEmail(toEmail, IC_PLANE + " Password Reset Request", html);
        System.out.println("Password reset email sent to: " + toEmail);
    }

    @Async
    public void sendBookingConfirmation(Booking booking) {
        String subject = IC_PLANE + " Booking Confirmed — Flight " + booking.getFlight().getFlightNumber();
        String html = buildOneWayHtml(booking);

        sendEmail(
                booking.getUser().getEmail(),
                subject,
                html);
        System.out.println("Booking confirmation email sent to: " + booking.getUser().getEmail());
    }

    @Async
    public void sendRoundTripConfirmation(Booking outbound, Booking returnBooking) {
        String subject = IC_PLANE + " Round Trip Confirmed — "
                + outbound.getFlight().getFlightNumber()
                + " & " + returnBooking.getFlight().getFlightNumber();
        String html = buildRoundTripHtml(outbound, returnBooking);

        sendEmail(
                outbound.getUser().getEmail(),
                subject,
                html);
        System.out.println("Round trip confirmation email sent to: " + outbound.getUser().getEmail());
    }

    // ── HTML builders ───────────────────────────────────────────────────────

    private String buildVerificationHtml(String name, String code) {
        String body = header("Verify Your Email", "One last step before you take off") +
                greeting(name, "Thanks for signing up. Enter the code below to verify your email address.") +

                "<div style='padding:32px;text-align:center;'>" +

                "<p style='font-size:13px;color:" + MUTED + ";margin-bottom:16px;text-transform:uppercase;" +
                "letter-spacing:2px;'>Your verification code</p>" +

                "<div style='display:inline-block;background:" + CARD + ";border:1px solid " + BORDER + ";" +
                "border-radius:12px;padding:20px 48px;margin-bottom:16px;'>" +
                "<span style='font-family:monospace;font-size:36px;font-weight:700;letter-spacing:12px;color:" + GOLD
                + ";'>"
                + code + "</span>" +
                "</div>" +

                "<p style='font-size:12px;color:" + MUTED + ";margin-top:8px;'>" +
                "This code expires in <strong style='color:" + BLACK + ";'>15 minutes</strong>." +
                "</p>" +

                "<p style='font-size:12px;color:" + MUTED + ";margin-top:8px;'>" +
                "If you did not create an account, you can safely ignore this email." +
                "</p>" +
                "</div>" +

                footer();

        return wrapper(body);
    }

    private String buildOneWayHtml(Booking booking) {
        var flight = booking.getFlight();
        var user = booking.getUser();

        String departure = fmt(flight.getDepartureTime() != null
                ? flight.getDepartureTime().format(FORMATTER)
                : "N/A");
        String arrival = fmt(flight.getArrivalTime() != null
                ? flight.getArrivalTime().format(FORMATTER)
                : "N/A");
        String bookedOn = fmt(booking.getBookingTime() != null
                ? booking.getBookingTime().format(FORMATTER)
                : "N/A");

        double totalPrice = flight.getPrice() * booking.getPassengers();

        String body = header("Booking Confirmed", "Your flight has been successfully booked") +
                greeting(user.getName(), "Your booking is confirmed. Here is your complete trip summary.") +

                "<div style='padding:20px 32px 0;'>" +
                sectionTitle("Booking Summary") +
                "<table style='width:100%;border-collapse:collapse;'>" +
                row(IC_HASH, "Booking ID", "#" + booking.getId(), true) +
                row(IC_PLANE, "Flight Number", flight.getFlightNumber(), false) +
                row(IC_FROM, "From", flight.getSource(), true) +
                row(IC_TO, "To", flight.getDestination(), false) +
                row(IC_CLOCK, "Departure", departure, true) +
                row(IC_CLOCK, "Arrival", arrival, false) +
                row(IC_CALENDAR, "Booked On", bookedOn, true) +
                row(IC_REPEAT, "Trip Type", booking.getTripType(), false) +
                row(IC_MONEY, "Total Price", priceValue(totalPrice), true) +
                "</table></div>" +

                buildPassengerSection(booking.getPassengerDetails()) +
                footer();

        return wrapper(body);
    }

    private String buildRoundTripHtml(Booking outbound, Booking returnBooking) {
        var user = outbound.getUser();
        var outFlight = outbound.getFlight();
        var retFlight = returnBooking.getFlight();

        double totalPrice = (outFlight.getPrice() + retFlight.getPrice()) * outbound.getPassengers();
        String bookedOn = fmt(outbound.getBookingTime() != null
                ? outbound.getBookingTime().format(FORMATTER)
                : "N/A");

        String body = header("Round Trip Confirmed", "Both flights have been successfully booked") +
                greeting(user.getName(), "Your round trip is confirmed. Here are the details for both flights.") +

                "<div style='padding:20px 32px 0;'>" +
                sectionTitle("Booking Info") +
                "<table style='width:100%;border-collapse:collapse;'>" +
                row(IC_CALENDAR, "Booked On", bookedOn, true) +
                row(IC_USERS, "Passengers", String.valueOf(outbound.getPassengers()), false) +
                row(IC_MONEY, "Total Price", priceValue(totalPrice), true) +
                "</table></div>" +

                "<div style='padding:20px 32px 0;'>" +
                sectionTitle(IC_PLANE + " Outbound Flight &mdash; Booking #" + outbound.getId()) +
                "<table style='width:100%;border-collapse:collapse;'>" +
                flightRows(outFlight,
                        fmt(outFlight.getDepartureTime() != null
                                ? outFlight.getDepartureTime().format(FORMATTER)
                                : "N/A"),
                        fmt(outFlight.getArrivalTime() != null
                                ? outFlight.getArrivalTime().format(FORMATTER)
                                : "N/A"))
                + "</table></div>" +

                "<div style='padding:20px 32px 0;'>" +
                sectionTitle("&#8617; Return Flight &mdash; Booking #" + returnBooking.getId()) +
                "<table style='width:100%;border-collapse:collapse;'>" +
                flightRows(retFlight,
                        fmt(retFlight.getDepartureTime() != null
                                ? retFlight.getDepartureTime().format(FORMATTER)
                                : "N/A"),
                        fmt(retFlight.getArrivalTime() != null
                                ? retFlight.getArrivalTime().format(FORMATTER)
                                : "N/A"))
                + "</table></div>" +

                buildPassengerSection(outbound.getPassengerDetails()) +
                footer();

        return wrapper(body);
    }

    private String buildPassengerSection(String passengerDetailsJson) {
        if (passengerDetailsJson == null || passengerDetailsJson.isBlank())
            return "";
        try {
            ObjectMapper mapper = new ObjectMapper();
            var passengers = mapper.readTree(passengerDetailsJson);
            if (!passengers.isArray() || passengers.isEmpty())
                return "";

            StringBuilder sb = new StringBuilder();
            sb.append("<div style='padding:20px 32px 0;'>")
                    .append(sectionTitle("Passenger Details"));

            for (int i = 0; i < passengers.size(); i++) {
                var p = passengers.get(i);
                sb.append(
                        "<div style='background:" + CARD + ";border:1px solid " + BORDER + ";" +
                                "border-radius:8px;padding:16px;margin-top:12px;margin-bottom:4px;'>" +

                                "<div style='display:flex;align-items:center;margin-bottom:12px;" +
                                "padding-bottom:10px;border-bottom:1px solid " + BORDER + ";'>" +
                                "<span style='font-size:18px;margin-right:8px;'>" + IC_USERS + "</span>" +
                                "<span style='color:" + GOLD + ";font-weight:700;font-size:13px;" +
                                "text-transform:uppercase;letter-spacing:1px;'>Passenger " + (i + 1) + "</span>" +
                                "</div>" +

                                "<table style='width:100%;border-collapse:collapse;font-size:13px;'>" +
                                miniRow(IC_PERSON, "Full Name", p.path("fullName").asString()) +
                                miniRow(IC_PASSPORT, "Passport No.", p.path("passportNumber").asString()) +
                                miniRow(IC_GLOBE, "Nationality", p.path("nationality").asString()) +
                                miniRow(IC_CAKE, "Date of Birth", p.path("dateOfBirth").asString()) +
                                miniRow(IC_GENDER, "Gender", p.path("gender").asString()) +
                                miniRow(IC_PHONE, "Phone", p.path("phone").asString()) +
                                miniRow(IC_MEAL, "Meal Preference", p.path("mealPreference").asString()) +
                                "</table></div>");
            }

            sb.append("</div>");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Could not parse passenger details: " + e.getMessage());
            return "";
        }
    }

    private static String fmt(String s) {
        return s;
    }
}