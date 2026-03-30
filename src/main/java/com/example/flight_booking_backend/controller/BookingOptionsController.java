package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.BookingOption;
import com.example.flight_booking_backend.repository.BookingOptionRepository;
import com.example.flight_booking_backend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/booking-options")
public class BookingOptionsController {

    private static final String NATIONALITY = "NATIONALITY";
    private static final String MEAL = "MEAL";

    private final BookingOptionRepository repo;
    private final JwtUtil jwtUtil;

    public BookingOptionsController(BookingOptionRepository repo, JwtUtil jwtUtil) {
        this.repo = repo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/nationalities")
    public ResponseEntity<?> getNationalities() {
        return ResponseEntity.ok(repo.findByType(NATIONALITY));
    }

    @PostMapping("/nationalities")
    public ResponseEntity<?> addNationality(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("Name is required");
        if (repo.existsByTypeAndNameIgnoreCase(NATIONALITY, name.trim()))
            return ResponseEntity.badRequest().body("Nationality already exists");

        BookingOption saved = repo.save(new BookingOption(NATIONALITY, name.trim()));
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/nationalities/{id}")
    public ResponseEntity<?> updateNationality(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        BookingOption option = repo.findById(id).orElse(null);
        if (option == null || !NATIONALITY.equals(option.getType()))
            return ResponseEntity.badRequest().body("Nationality not found");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("Name is required");

        option.setName(name.trim());
        return ResponseEntity.ok(repo.save(option));
    }

    @DeleteMapping("/nationalities/{id}")
    public ResponseEntity<?> deleteNationality(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        if (!repo.existsById(id))
            return ResponseEntity.badRequest().body("Nationality not found");

        repo.deleteById(id);
        return ResponseEntity.ok("Nationality deleted");
    }

    @GetMapping("/meals")
    public ResponseEntity<?> getMeals() {
        return ResponseEntity.ok(repo.findByType(MEAL));
    }

    @PostMapping("/meals")
    public ResponseEntity<?> addMeal(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("Name is required");
        if (repo.existsByTypeAndNameIgnoreCase(MEAL, name.trim()))
            return ResponseEntity.badRequest().body("Meal preference already exists");

        BookingOption saved = repo.save(new BookingOption(MEAL, name.trim()));
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/meals/{id}")
    public ResponseEntity<?> updateMeal(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        BookingOption option = repo.findById(id).orElse(null);
        if (option == null || !MEAL.equals(option.getType()))
            return ResponseEntity.badRequest().body("Meal preference not found");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("Name is required");

        option.setName(name.trim());
        return ResponseEntity.ok(repo.save(option));
    }

    @DeleteMapping("/meals/{id}")
    public ResponseEntity<?> deleteMeal(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied: ADMIN role required");

        if (!repo.existsById(id))
            return ResponseEntity.badRequest().body("Meal preference not found");

        repo.deleteById(id);
        return ResponseEntity.ok("Meal preference deleted");
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return false;
        try {
            String role = jwtUtil.extractRole(authHeader.substring(7));
            return "ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }
}