package com.example.flight_booking_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.flight_booking_backend.model.PasswordResetToken;
import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.PasswordResetRepository;
import com.example.flight_booking_backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin.client-secret}")
    private String adminClientSecret;

    private final PasswordResetRepository tokenRepo;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public AuthService(PasswordResetRepository tokenRepo,
            EmailService emailService,
            UserRepository userRepository) {
        this.tokenRepo = tokenRepo;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

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

        if (newPassword.length() < 6) {
            return "Password must be at least 6 characters long.";
        }

        if (!newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*") || !newPassword.matches(".*\\d.*")) {
            return "Password must contain at least one uppercase letter, one lowercase letter, and one number.";
        }

        User user = userRepository.findByEmail(resetToken.getEmail()).orElse(null);
        if (user == null)
            return "User not found.";

        boolean updated = updateKeycloakPassword(user.getKeycloakId(), newPassword);
        if (!updated)
            return "Failed to update password in Keycloak.";

        tokenRepo.delete(resetToken);
        return "Password reset successful.";
    }

    private String getAdminAccessToken() {
        String url = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=client_credentials"
                + "&client_id=" + adminClientId
                + "&client_secret=" + adminClientSecret;

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        url, HttpMethod.POST,
        new HttpEntity<>(body, headers),
        new ParameterizedTypeReference<Map<String, Object>>() {});

return (String) response.getBody().get("access_token");
    }

    private boolean updateKeycloakPassword(String keycloakUserId, String newPassword) {
        try {
            String adminToken = getAdminAccessToken();
            String url = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/reset-password";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminToken);

            Map<String, Object> payload = Map.of(
                    "type", "password",
                    "value", newPassword,
                    "temporary", false);

            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(payload, headers), Void.class);
            return true;
        } catch (Exception e) {
            System.err.println("Keycloak password update failed: " + e.getMessage());
            return false;
        }
    }
}