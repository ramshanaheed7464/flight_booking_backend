package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized");

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {

        String email = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized");

        String newName = body.get("name");
        if (newName != null && !newName.isBlank()) {
            user.setName(newName.trim());
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }
}