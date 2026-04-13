package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.PasswordResetToken;
import com.example.flight_booking_backend.repository.PasswordResetTokenRepository;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.service.EmailService;
import com.example.flight_booking_backend.service.KeycloakAdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${frontend.url}")
    private String frontendUrl;

    public AuthController(UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            KeycloakAdminService keycloakAdminService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.keycloakAdminService = keycloakAdminService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body("Email is required");

        // Always return success to prevent email enumeration
        if (!userRepository.existsByEmail(email.trim())) {
            return ResponseEntity.ok("If an account exists, a reset link has been sent.");
        }

        // Delete any existing tokens for this email
        tokenRepository.deleteByEmail(email.trim());

        // Generate token valid for 15 minutes
        String token = UUID.randomUUID().toString();
        tokenRepository.save(new PasswordResetToken(
                token,
                email.trim(),
                LocalDateTime.now().plusMinutes(15)));

        // Send email via Brevo REST API
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        emailService.sendResetEmail(email.trim(), resetLink);

        return ResponseEntity.ok("If an account exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || newPassword == null)
            return ResponseEntity.badRequest().body("Token and new password are required");

        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);

        if (resetToken == null || resetToken.isUsed())
            return ResponseEntity.badRequest().body("Invalid or already used reset link");

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now()))
            return ResponseEntity.badRequest().body("Reset link has expired");

        // Update password in Keycloak
        try {
            keycloakAdminService.updatePassword(resetToken.getEmail(), newPassword);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to reset password");
        }

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return ResponseEntity.ok("Password reset successfully");
    }
}