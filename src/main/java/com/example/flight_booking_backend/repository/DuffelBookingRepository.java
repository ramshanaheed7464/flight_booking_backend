package com.example.flight_booking_backend.repository;

import com.example.flight_booking_backend.model.DuffelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DuffelBookingRepository extends JpaRepository<DuffelBooking, Long> {
    List<DuffelBooking> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}
