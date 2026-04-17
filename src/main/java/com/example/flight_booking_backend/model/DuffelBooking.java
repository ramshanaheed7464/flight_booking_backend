package com.example.flight_booking_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "duffel_booking")
public class DuffelBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bookingReference;
    private String origin;
    private String destination;
    private String departureAt;
    private String carrier;

    /** Email of the authenticated user who made the booking. */
    private String userEmail;

    /** JSON array of passenger objects (name, email, passportNumber, etc.). */
    @Column(columnDefinition = "TEXT")
    private String passengerDetailsJson;

    private LocalDateTime createdAt;

    public DuffelBooking() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDepartureAt() { return departureAt; }
    public void setDepartureAt(String departureAt) { this.departureAt = departureAt; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getPassengerDetailsJson() { return passengerDetailsJson; }
    public void setPassengerDetailsJson(String passengerDetailsJson) { this.passengerDetailsJson = passengerDetailsJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
