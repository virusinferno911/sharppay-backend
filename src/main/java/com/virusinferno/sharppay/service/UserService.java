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
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

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

        String generatedOtp = String.format("%06d", new Random().nextInt(999999));
        newUser.setOtpCode(generatedOtp);
        newUser.setEmailVerified(false);

        User savedUser = userRepository.save(newUser);

        Account newAccount = new Account();
        newAccount.setUser(savedUser);
        newAccount.setAccountNumber(generateUniqueAccountNumber());
        accountRepository.save(newAccount);

        emailService.sendOtpEmail(savedUser.getEmail(), generatedOtp);
        return savedUser;
    }

    public String verifyOtp(String email, String otpCode) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));

        if (user.getOtpCode() != null && user.getOtpCode().equals(otpCode)) {
            user.setEmailVerified(true);
            user.setOtpCode(null);
            userRepository.save(user);
            emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
            return "Email verified successfully!";
        }
        throw new RuntimeException("Invalid OTP Code!");
    }

    public LoginResponse loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Invalid email or password!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password!");
        }
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email via OTP before logging in.");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new LoginResponse(token, "Login successful!");
    }

    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));
        Account account = accountRepository.findByUser(user).orElseThrow(() -> new RuntimeException("Account not found for this user!"));

        return UserProfileResponse.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .kycStatus(user.getKycStatus())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .livenessTransferLimit(user.getLivenessTransferLimit() != null ? user.getLivenessTransferLimit() : new BigDecimal("50000.00"))
                .hasTransactionPin(user.getTransactionPin() != null && !user.getTransactionPin().isEmpty())
                .build();
    }

    public String updateSecuritySettings(String email, UserSettingsRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));

        // RESTORED: This is the block I accidentally removed that actually saves the PIN!
        if (request.getTransactionPin() != null && !request.getTransactionPin().isEmpty()) {
            if (request.getTransactionPin().length() != 4 || !request.getTransactionPin().matches("\\d+")) {
                throw new RuntimeException("Transaction PIN must be exactly 4 digits!");
            }
            user.setTransactionPin(passwordEncoder.encode(request.getTransactionPin()));
        }

        if (request.getLivenessTransferLimit() != null) {
            if (request.getLivenessTransferLimit().compareTo(BigDecimal.ZERO) < 0) throw new RuntimeException("Transfer limit cannot be negative!");
            user.setLivenessTransferLimit(request.getLivenessTransferLimit());
        }
        userRepository.save(user);
        return "Security settings updated successfully!";
    }

    // ==========================================
    // RECOVERY & CHANGE LOGIC
    // ==========================================

    @Transactional
    public void changePin(String email, Map<String, String> request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));

        String oldPin = request.get("oldPin");
        String newPin = request.get("newPin");

        if (newPin == null || newPin.length() != 4) throw new RuntimeException("New PIN must be 4 digits!");

        // If user already has a PIN, strictly verify the old one
        if (user.getTransactionPin() != null) {
            if (oldPin == null || !passwordEncoder.matches(oldPin, user.getTransactionPin())) {
                throw new RuntimeException("Incorrect Old PIN!");
            }
        }

        user.setTransactionPin(passwordEncoder.encode(newPin));
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Account with this email does not exist."));
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setOtpCode(otp);
        userRepository.save(user);
        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public void resetPassword(Map<String, String> request) {
        User user = userRepository.findByEmail(request.get("email")).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.get("otpCode"))) {
            throw new RuntimeException("Invalid or Expired OTP!");
        }
        user.setPasswordHash(passwordEncoder.encode(request.get("newPassword")));
        user.setOtpCode(null);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setOtpCode(otp);
        userRepository.save(user);
        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public void resetPin(String email, Map<String, String> request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.get("otpCode"))) {
            throw new RuntimeException("Invalid or Expired OTP!");
        }
        if (request.get("newPin") == null || request.get("newPin").length() != 4) {
            throw new RuntimeException("New PIN must be 4 digits!");
        }
        user.setTransactionPin(passwordEncoder.encode(request.get("newPin")));
        user.setOtpCode(null);
        userRepository.save(user);
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