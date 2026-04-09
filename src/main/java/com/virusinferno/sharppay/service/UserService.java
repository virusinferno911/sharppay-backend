package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.LoginRequest;
import com.virusinferno.sharppay.dto.LoginResponse;
import com.virusinferno.sharppay.dto.UserProfileResponse;
import com.virusinferno.sharppay.dto.UserRegistrationRequest;
import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // <-- We wired up your new Key Machine here!

    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            throw new RuntimeException("A user with this email already exists!");
        }

        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setFullName(request.getFullName());
        newUser.setPhoneNumber(request.getPhoneNumber());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(newUser);

        Account newAccount = new Account();
        newAccount.setUser(savedUser);
        newAccount.setAccountNumber(generateUniqueAccountNumber());

        accountRepository.save(newAccount);

        return savedUser;
    }

    // THE LOGIN BRAIN
    public LoginResponse loginUser(LoginRequest request) {
        // 1. Find the user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password!"));

        // 2. Verify the password using the BCrypt decoder
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password!");
        }

        // 3. If correct, generate the 24-hour JWT token
        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(token, "Login successful!");
    }

    // NEW: Dashboard Profile Fetcher
    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found for this user!"));

        return UserProfileResponse.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .kycStatus(user.getKycStatus())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .build();
    }

    private String generateUniqueAccountNumber() {
        Random random = new Random();
        String accountNumber;
        do {
            long number = 8000000000L + (long)(random.nextDouble() * 1000000000L);
            accountNumber = String.valueOf(number);
        } while (accountRepository.findByAccountNumber(accountNumber).isPresent());

        return accountNumber;
    }
}