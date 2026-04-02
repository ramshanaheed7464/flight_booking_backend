package com.example.flight_booking_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flights")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String flightNumber;
    private String airline;
    private String source;
    private String destination;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer duration;
    private Integer seatsAvailable;
    private Double price;
    // private String cabinClass;
    // private String stopovers;
    private String baggageAllowance;
    private String inFlightEntertainment;
    private Boolean wifiAvailability;
    private String seatType;
    private Boolean mealsIncluded;
    private Boolean refundable;
    private Boolean isEntertainmentAvailable;

    @Column
    private String meals;

    @OneToMany(mappedBy = "flight")
    @JsonIgnore
    private List<Booking> bookings;

    public Flight() {
    }

    public Flight(String flightNumber, String airline, String source, String destination,
            LocalDateTime departureTime, LocalDateTime arrivalTime, Integer duration, Integer seatsAvailable,
            Double price,
            // String cabinClass,
            // String layover, String stopovers,
            String baggageAllowance, String inFlightEntertainment,
            String wifiAvailability, String seatType, String mealsIncluded, boolean refundable,
            boolean isEntertainmentAvailable) {
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.source = source;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.duration = duration;
        this.seatsAvailable = seatsAvailable;
        this.price = price;
        // this.cabinClass = cabinClass;
        // this.layover = layover;
        // this.stopovers = stopovers;
        this.baggageAllowance = baggageAllowance;
        this.inFlightEntertainment = inFlightEntertainment;
        this.wifiAvailability = false; // default; use setter after construction
        this.seatType = seatType;
        this.mealsIncluded = false; // default; use setter after construction
        this.refundable = refundable;
        this.isEntertainmentAvailable = isEntertainmentAvailable;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getSeatsAvailable() {
        return seatsAvailable;
    }

    public void setSeatsAvailable(Integer seatsAvailable) {
        this.seatsAvailable = seatsAvailable;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getMeals() {
        return meals;
    }

    public void setMeals(String meals) {
        this.meals = meals;
    }

    // public String getCabinClass() {
    // return cabinClass;
    // }

    // public void setCabinClass(String cabinClass) {
    // this.cabinClass = cabinClass;
    // }

    // public String getLayover() {
    // return layover;
    // }

    // public void setLayover(String layover) {
    // this.layover = layover;
    // }

    // public String getStopovers() {
    // return stopovers;
    // }

    // public void setStopovers(String stopovers) {
    // this.stopovers = stopovers;
    // }

    public String getBaggageAllowance() {
        return baggageAllowance;
    }

    public void setBaggageAllowance(String baggageAllowance) {
        this.baggageAllowance = baggageAllowance;
    }

    public String getInFlightEntertainment() {
        return inFlightEntertainment;
    }

    public void setInFlightEntertainment(String inFlightEntertainment) {
        this.inFlightEntertainment = inFlightEntertainment;
    }

    @JsonProperty("entertainmentAvailable")
    public boolean getEntertainmentAvailability() {
        return isEntertainmentAvailable;
    }

    public void setEntertainmentAvailability(boolean isEntertainmentAvailable) {
        this.isEntertainmentAvailable = isEntertainmentAvailable;
    }

    @JsonProperty("wifiAvailable")
    public boolean getWifiAvailability() {
        return wifiAvailability;
    }

    public void setWifiAvailability(Boolean wifiAvailability) {
        this.wifiAvailability = wifiAvailability;
    }

    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }

    public boolean getMealsIncluded() {
        return mealsIncluded;
    }

    public void setMealsIncluded(Boolean mealsIncluded) {
        this.mealsIncluded = mealsIncluded;
    }

    public boolean isRefundable() {
        return refundable;
    }

    public void setRefundable(boolean refundable) {
        this.refundable = refundable;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }
}