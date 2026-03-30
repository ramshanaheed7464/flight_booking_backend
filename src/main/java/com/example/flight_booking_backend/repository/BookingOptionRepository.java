package com.example.flight_booking_backend.repository;

import com.example.flight_booking_backend.model.BookingOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingOptionRepository extends JpaRepository<BookingOption, Long> {
    List<BookingOption> findByType(String type);

    boolean existsByTypeAndNameIgnoreCase(String type, String name);
}