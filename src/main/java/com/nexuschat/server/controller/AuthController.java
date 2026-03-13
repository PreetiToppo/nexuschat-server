package com.nexuschat.server.controller;

import com.nexuschat.server.model.User;
import com.nexuschat.server.repository.UserRepository;
import com.nexuschat.server.service.JwtService;
import com.nexuschat.server.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        // Rate limit by IP
        String ip = getClientIp(httpRequest);
        if (!rateLimiterService.isAllowed("register:" + ip)) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "Too many attempts. Please try again later."));
        }

        String username = request.get("username");
        String email    = request.get("email");
        String password = request.get("password");

        // ── Null checks ────────────────────────────────────────────────
        if (username == null || email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, email and password are required"));
        }

        // ── Username validation ────────────────────────────────────────
        username = username.trim();
        if (username.length() < 3 || username.length() > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username must be 3–20 characters"));
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username can only contain letters, numbers, and underscores"));
        }

        // ── Email validation ───────────────────────────────────────────
        email = email.trim().toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid email address"));
        }

        // ── Password validation ────────────────────────────────────────
        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 8 characters"));
        }
        if (!password.matches(".*[A-Z].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must contain at least one uppercase letter"));
        }
        if (!password.matches(".*[0-9].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must contain at least one number"));
        }

        // ── Uniqueness checks ──────────────────────────────────────────
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email already registered"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username already taken"));
        }

        // ── Save user ──────────────────────────────────────────────────
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        User saved = userRepository.save(user);

        // Reset rate limit on success
        rateLimiterService.resetLimit("register:" + ip);

        String accessToken  = jwtService.generateAccessToken(saved.getId(), saved.getEmail());
        String refreshToken = jwtService.generateRefreshToken(saved.getId());

        return ResponseEntity.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "userId",       saved.getId(),
                "username",     saved.getUsername()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        // Rate limit by IP
        String ip = getClientIp(httpRequest);
        if (!rateLimiterService.isAllowed("login:" + ip)) {
            int remaining = rateLimiterService.getRemainingAttempts("login:" + ip);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many login attempts. Please try again in 15 minutes.",
                    "remainingAttempts", remaining
            ));
        }

        String email    = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email and password are required"));
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);

        // Use constant-time comparison to prevent timing attacks
        // Always run passwordEncoder.matches even if user is null
        String hashToCheck = (user != null)
                ? user.getPasswordHash()
                : "$2a$10$dummyhashtopreventtimingattack00000000000000000000000000";

        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);

        if (user == null || !passwordMatches) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Invalid email or password",
                            "remainingAttempts", rateLimiterService.getRemainingAttempts("login:" + ip)
                    ));
        }

        // Reset rate limit on successful login
        rateLimiterService.resetLimit("login:" + ip);

        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return ResponseEntity.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "userId",       user.getId(),
                "username",     user.getUsername()
        ));
    }

    // ── Helper ─────────────────────────────────────────────────────────────
    // Handles proxies / load balancers (X-Forwarded-For)
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}