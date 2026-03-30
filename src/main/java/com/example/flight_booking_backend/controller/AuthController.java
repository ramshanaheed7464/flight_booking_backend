package com.example.flight_booking_backend.controller;

import com.example.flight_booking_backend.model.User;
import com.example.flight_booking_backend.repository.UserRepository;
import com.example.flight_booking_backend.security.JwtUtil;
import com.example.flight_booking_backend.service.AuthService;
import com.example.flight_booking_backend.service.EmailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Autowired
    private AuthService authService;

    @Value("${admin.signup.secret}")
    private String adminSignupSecret;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");
        String name = body.getOrDefault("name", "").trim();

        if (email.isBlank() || password.isBlank() || name.isBlank())
            return ResponseEntity.badRequest().body("Name, email and password are required.");
        if (userRepository.existsByEmail(email))
            return ResponseEntity.badRequest().body("Email already exists");

        String code = generateCode();

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setVerified(false);
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendVerificationCode(email, name, code);

        return ResponseEntity.ok(Map.of(
                "message", "Verification code sent to " + email,
                "email", email));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String code = body.getOrDefault("code", "").trim();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.badRequest().body("User not found.");
        if (user.isVerified())
            return ResponseEntity.badRequest().body("Email already verified.");
        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code))
            return ResponseEntity.badRequest().body("Invalid verification code.");
        if (user.getVerificationExpiry() == null ||
                LocalDateTime.now().isAfter(user.getVerificationExpiry()))
            return ResponseEntity.badRequest().body("Verification code has expired. Please register again.");

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully.",
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "role", user.getRole())));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return ResponseEntity.badRequest().body("User not found.");
        if (user.isVerified())
            return ResponseEntity.badRequest().body("Email already verified.");

        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendVerificationCode(email, user.getName(), code);

        return ResponseEntity.ok("Verification code resent to " + email);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        User u = userRepository.findByEmail(email).orElse(null);
        if (u == null || !passwordEncoder.matches(password, u.getPassword()))
            return ResponseEntity.badRequest().body("Invalid credentials");
        if (!u.isVerified())
            return ResponseEntity.status(403).body("EMAIL_NOT_VERIFIED");

        String token = jwtUtil.generateToken(u.getEmail(), u.getRole());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", u.getId(),
                        "name", u.getName(),
                        "email", u.getEmail(),
                        "role", u.getRole())));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody Map<String, String> body) {
        String secret = body.getOrDefault("secret", "");
        if (!constantTimeEquals(secret, adminSignupSecret))
            return ResponseEntity.badRequest().body("Email already exists");

        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");
        String name = body.getOrDefault("name", "Administrator").trim();

        if (email.isBlank() || password.isBlank())
            return ResponseEntity.badRequest().body("Email and password are required.");
        if (password.length() < 8)
            return ResponseEntity.badRequest().body("Password must be at least 8 characters.");
        if (userRepository.existsByEmail(email))
            return ResponseEntity.badRequest().body("Email already exists");

        User admin = new User();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        admin.setVerified(true);
        userRepository.save(admin);

        String token = jwtUtil.generateToken(email, "ADMIN");

        return ResponseEntity.ok(Map.of(
                "message", "Admin account created successfully.",
                "token", token,
                "user", Map.of(
                        "id", admin.getId(),
                        "name", admin.getName(),
                        "email", admin.getEmail(),
                        "role", admin.getRole())));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        if (email.isBlank())
            return ResponseEntity.badRequest().body("Email is required.");

        authService.forgotPassword(email);

        return ResponseEntity.ok("If an account with that email exists, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.getOrDefault("token", "").trim();
        String newPassword = body.getOrDefault("newPassword", "");

        if (token.isBlank() || newPassword.isBlank())
            return ResponseEntity.badRequest().body("Token and new password are required.");

        if (newPassword.length() < 6)
            return ResponseEntity.badRequest().body("Password must be at least 6 characters long.");
        if (!newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*") || !newPassword.matches(".*\\d.*"))
            return ResponseEntity.badRequest()
                    .body("Password must contain at least one uppercase letter, one lowercase letter, and one number.");
        String result = authService.resetPassword(token, newPassword);
        if (result.equals("Password reset successful."))
            return ResponseEntity.ok(result);
        else
            return ResponseEntity.badRequest().body(result);
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null)
            return false;
        if (a.length() != b.length())
            return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++)
            result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}