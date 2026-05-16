package com.connecthub.authservice.controller;

import com.connecthub.authservice.dto.UserSummaryResponse;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.UserRepository;
import com.connecthub.authservice.security.JwtUtil;
import com.connecthub.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints, secured internally via JWT role validation.
 * The gateway allows all /auth/** as public, so we enforce ADMIN role
 * manually by parsing the JWT and checking the user's role in the DB.
 *
 * Route: /auth/admin/**
 */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────────────
    // Internal helper: extract caller's userId from Bearer token, verify ADMIN role
    // ──────────────────────────────────────────────────────────────────────────────
    private void requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        String callerId;
        try {
            callerId = jwtUtil.extractSubject(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
        }
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject not found."));
        if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // GET /auth/admin/users  — list all users
    // ──────────────────────────────────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return ResponseEntity.ok(authService.getAllUsersForAdmin());
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // PUT /auth/admin/users/{userId}/block  — toggle block/unblock
    // ──────────────────────────────────────────────────────────────────────────────
    @PutMapping("/users/{userId}/block")
    public ResponseEntity<UserSummaryResponse> toggleBlock(
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        String callerId = extractSubjectOrThrow(authHeader);
        if (callerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot block your own account.");
        }
        return ResponseEntity.ok(authService.toggleUserBlockStatus(userId));
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DELETE /auth/admin/users/{userId}  — permanently remove user
    // ──────────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        String callerId = extractSubjectOrThrow(authHeader);
        if (callerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own admin account.");
        }
        authService.deleteUserAsAdmin(userId);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully."));
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // PUT /auth/admin/users/{userId}/role  — promote or demote role
    // ──────────────────────────────────────────────────────────────────────────────
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummaryResponse> changeRole(
            @PathVariable String userId,
            @RequestParam String role,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be ADMIN or USER.");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        target.setRole(role.toUpperCase());
        userRepository.save(target);
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // PUT /auth/admin/users/{userId}/credits  — set translation credits
    // ──────────────────────────────────────────────────────────────────────────────
    @PutMapping("/users/{userId}/credits")
    public ResponseEntity<UserSummaryResponse> setCredits(
            @PathVariable String userId,
            @RequestParam int credits,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        if (credits < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credits cannot be negative.");
        }
        return ResponseEntity.ok(authService.adminSetCredits(userId, credits));
    }

    private String extractSubjectOrThrow(String authHeader) {
        try {
            return jwtUtil.extractSubject(authHeader.substring(BEARER_PREFIX.length()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
        }
    }
}
