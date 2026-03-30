package com.example.flight_booking_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.flight_booking_backend.model.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
}
