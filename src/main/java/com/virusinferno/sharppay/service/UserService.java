package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.LoginRequest;
import com.virusinferno.sharppay.dto.LoginResponse;
import com.virusinferno.sharppay.dto.UserProfileResponse;
import com.virusinferno.sharppay.dto.UserRegistrationRequest;
import com.virusinferno.sharppay.dto.UserSettingsRequest;
import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

    public LoginResponse loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password!");
        }

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(token, "Login successful!");
    }

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

    // ==========================================
    // SHARPPAY V2: SECURITY SETTINGS ENGINE
    // ==========================================
    public String updateSecuritySettings(String email, UserSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // 1. Handle the PIN Update
        if (request.getTransactionPin() != null) {
            if (request.getTransactionPin().length() != 4 || !request.getTransactionPin().matches("\\d+")) {
                throw new RuntimeException("Transaction PIN must be exactly 4 digits!");
            }
            // Securely hash the PIN before saving
            user.setTransactionPin(passwordEncoder.encode(request.getTransactionPin()));
        }

        // 2. Handle the Custom Liveness Limit Update
        if (request.getLivenessTransferLimit() != null) {
            if (request.getLivenessTransferLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Transfer limit cannot be negative!");
            }
            user.setLivenessTransferLimit(request.getLivenessTransferLimit());
        }

        userRepository.save(user);
        return "Security settings updated successfully!";
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