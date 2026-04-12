package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.City;
import com.example.flight_booking_backend.repository.CityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final CityRepository cityRepo;

    public LocationController(CityRepository cityRepo) {
        this.cityRepo = cityRepo;
    }

    @GetMapping("/cities")
    public ResponseEntity<List<City>> getCities() {
        return ResponseEntity.ok(cityRepo.findAllByOrderByNameAsc());
    }

    @PostMapping("/cities")
    public ResponseEntity<?> addCity(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied");

        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body("City name is required");
        if (cityRepo.existsByNameIgnoreCase(name.trim()))
            return ResponseEntity.badRequest().body("City already exists");

        return ResponseEntity.ok(cityRepo.save(new City(name.trim())));
    }

    @PutMapping("/cities/{id}")
    public ResponseEntity<?> updateCity(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        if (!isAdmin(jwt))
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

    @DeleteMapping("/cities/{id}")
    public ResponseEntity<?> deleteCity(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Access denied");

        if (!cityRepo.existsById(id))
            return ResponseEntity.badRequest().body("City not found");

        cityRepo.deleteById(id);
        return ResponseEntity.ok("City deleted");
    }

    private boolean isAdmin(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null)
                return false;
            List<?> roles = (List<?>) realmAccess.get("roles");
            return roles != null && (roles.contains("ADMIN") || roles.contains("admin"));
        } catch (Exception e) {
            return false;
        }
    }
}