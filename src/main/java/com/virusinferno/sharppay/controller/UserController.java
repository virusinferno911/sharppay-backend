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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody UserRegistrationRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok("Welcome to Sharp-pay! User registered successfully.");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.loginUser(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        UserProfileResponse profile = userService.getUserProfile(principal.getName());
        return ResponseEntity.ok(profile);
    }

    // ==========================================
    // SHARPPAY V2: SETTINGS ENDPOINT
    // ==========================================
    @PostMapping("/settings")
    public ResponseEntity<String> updateSettings(@RequestBody UserSettingsRequest request, Principal principal) {
        // Securely identify the user via their JWT
        String result = userService.updateSecuritySettings(principal.getName(), request);
        return ResponseEntity.ok(result);
    }
}