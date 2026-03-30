package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // GET /api/user/me — get current user profile
    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        User user = extractUser(authHeader);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized");

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }

    // PUT /api/user/me — update name and/or password
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        User user = extractUser(authHeader);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized");

        String newName = body.get("name");
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        // Update name if provided
        if (newName != null && !newName.isBlank()) {
            user.setName(newName.trim());
        }

        // Update password if provided
        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || currentPassword.isBlank()) {
                return ResponseEntity.badRequest().body("Current password is required to set a new password.");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ResponseEntity.badRequest().body("Current password is incorrect.");
            }
            if (newPassword.length() < 6) {
                return ResponseEntity.badRequest().body("New password must be at least 6 characters.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }

    private User extractUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            String email = jwtUtil.extractClaims(authHeader.substring(7)).getSubject();
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}