package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.service.KeycloakAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    public UserController(UserRepository userRepository, KeycloakAdminService keycloakAdminService) {
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncUser(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name") != null
                ? jwt.getClaimAsString("name")
                : jwt.getClaimAsString("preferred_username");

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        String role = "USER";
        if (realmAccess != null) {
            java.util.List<?> roles = (java.util.List<?>) realmAccess.get("roles");
            if (roles != null && (roles.contains("ADMIN") || roles.contains("admin")))
                role = "ADMIN";
        }

        final String resolvedRole = role;

        userRepository.findByKeycloakId(keycloakId).ifPresentOrElse(
                existing -> {
                    existing.setEmail(email);
                    if (existing.getName() == null || existing.getName().isBlank()) {
                        existing.setName(name);
                    }
                    userRepository.save(existing);
                },
                () -> userRepository.save(new User(name, email, keycloakId, resolvedRole)));

        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body("User profile not found. Please sync your account.");

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body("User profile not found. Please sync your account.");
        }
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (currentPassword == null || newPassword == null)
            return ResponseEntity.badRequest().body("Current and New Passwords are required");
        try {
            keycloakAdminService.verifyAndUpdatePassword(email, currentPassword, newPassword);
            return ResponseEntity.ok("Password changed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {

        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body("User profile not found. Please sync your account.");

        String newName = body.get("name");
        if (newName != null && !newName.isBlank())
            user.setName(newName.trim());

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }
}