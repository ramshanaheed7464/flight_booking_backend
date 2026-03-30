package com.example.flight_booking_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.flight_booking_backend.model.PasswordResetToken;
import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.PasswordResetRepository;
import com.example.flight_booking_backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private PasswordResetRepository tokenRepo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void forgotPassword(String email) {

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setEmail(email);
        resetToken.setToken(token);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30));

        tokenRepo.save(resetToken);

        if (userRepository.existsByEmail(email)) {
            emailService.sendResetEmail(email, token);
        }
    }

    public String resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepo.findByToken(token).orElse(null);
        if (resetToken == null || resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return "Invalid or expired token.";
        }

        if (LocalDateTime.now().isAfter(resetToken.getExpiryDate())) {
            tokenRepo.delete(resetToken);
            return "Token has expired.";
        }

        if (newPassword.length() < 6) {
            return "Password must be at least 6 characters long.";
        }

        if (!newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*") || !newPassword.matches(".*\\d.*")) {
            return "Password must contain at least one uppercase letter, one lowercase letter, and one number.";
        }

        User user = userRepository.findByEmail(resetToken.getEmail()).orElse(null);
        if (user == null) {
            return "User not found.";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepo.delete(resetToken);
        return "Password reset successful.";
    }
}
