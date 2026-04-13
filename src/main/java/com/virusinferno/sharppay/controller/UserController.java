package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.LoginRequest;
import com.virusinferno.sharppay.dto.LoginResponse;
import com.virusinferno.sharppay.dto.UserProfileResponse;
import com.virusinferno.sharppay.dto.UserRegistrationRequest;
import com.virusinferno.sharppay.dto.UserSettingsRequest;
import com.virusinferno.sharppay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody UserRegistrationRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok("Registration successful! Please check your email for the OTP.");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(userService.verifyOtp(request.get("email"), request.get("otpCode")));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.loginUser(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        return ResponseEntity.ok(userService.getUserProfile(principal.getName()));
    }

    @PostMapping("/settings")
    public ResponseEntity<String> updateSettings(@RequestBody UserSettingsRequest request, Principal principal) {
        return ResponseEntity.ok(userService.updateSecuritySettings(principal.getName(), request));
    }

    // ==========================================
    // NEW: ADVANCED PIN & PASSWORD RECOVERY
    // ==========================================

    @PostMapping("/change-pin")
    public ResponseEntity<String> changePin(@RequestBody Map<String, String> request, Principal principal) {
        userService.changePin(principal.getName(), request);
        return ResponseEntity.ok("Transaction PIN updated securely!");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> request) {
        userService.forgotPassword(request.get("email"));
        return ResponseEntity.ok("OTP sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        userService.resetPassword(request);
        return ResponseEntity.ok("Password has been reset successfully!");
    }

    @PostMapping("/forgot-pin")
    public ResponseEntity<String> forgotPin(Principal principal) {
        userService.forgotPin(principal.getName());
        return ResponseEntity.ok("OTP sent to your email to reset PIN.");
    }

    @PostMapping("/reset-pin")
    public ResponseEntity<String> resetPin(@RequestBody Map<String, String> request, Principal principal) {
        userService.resetPin(principal.getName(), request);
        return ResponseEntity.ok("Transaction PIN has been reset successfully!");
    }
}