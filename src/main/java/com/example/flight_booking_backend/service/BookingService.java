package com.example.flight_booking_backend.service;

import com.example.flight_booking_backend.model.Booking;
import com.example.flight_booking_backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }
}