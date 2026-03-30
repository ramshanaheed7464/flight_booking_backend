package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.City;
import com.example.flight_booking_backend.repository.CityRepository;
import com.example.flight_booking_backend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final CityRepository cityRepo;
    private final JwtUtil jwtUtil;

    public LocationController(CityRepository cityRepo, JwtUtil jwtUtil) {
        this.cityRepo = cityRepo;
        this.jwtUtil = jwtUtil;
    }

    // GET /api/locations/cities — public, returns all cities sorted by name
    @GetMapping("/cities")
    public ResponseEntity<List<City>> getCities() {
        return ResponseEntity.ok(cityRepo.findAllByOrderByNameAsc());
    }

    // POST /api/locations/cities — admin only
    @PostMapping("/cities")
    public ResponseEntity<?> addCity(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("City name is required");
        if (cityRepo.existsByNameIgnoreCase(name.trim()))
            return ResponseEntity.badRequest().body("City already exists");

        return ResponseEntity.ok(cityRepo.save(new City(name.trim())));
    }

    // PUT /api/locations/cities/{id} — admin only
    @PutMapping("/cities/{id}")
    public ResponseEntity<?> updateCity(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied");

        City city = cityRepo.findById(id).orElse(null);
        if (city == null)
            return ResponseEntity.badRequest().body("City not found");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("City name is required");
        if (cityRepo.existsByNameIgnoreCase(name.trim()))
            return ResponseEntity.badRequest().body("City already exists");

        city.setName(name.trim());
        return ResponseEntity.ok(cityRepo.save(city));
    }

    // DELETE /api/locations/cities/{id} — admin only
    @DeleteMapping("/cities/{id}")
    public ResponseEntity<?> deleteCity(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader))
            return ResponseEntity.status(403).body("Access denied");

        if (!cityRepo.existsById(id))
            return ResponseEntity.badRequest().body("City not found");

        cityRepo.deleteById(id);
        return ResponseEntity.ok("City deleted");
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return false;
        try {
            return "ADMIN".equalsIgnoreCase(jwtUtil.extractRole(authHeader.substring(7)));
        } catch (Exception e) {
            return false;
        }
    }
}