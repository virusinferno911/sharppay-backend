package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.CardCreateRequest;
import com.virusinferno.sharppay.dto.CardResponse;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.model.VirtualCard;
import com.virusinferno.sharppay.repository.UserRepository;
import com.virusinferno.sharppay.repository.VirtualCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VirtualCardService {

    private final VirtualCardRepository virtualCardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CardResponse createCard(String email, CardCreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // Check if user already has a card (1 card per user policy for now)
        Optional<VirtualCard> existingCard = virtualCardRepository.findByUser(user);
        if (existingCard.isPresent()) {
            throw new RuntimeException("You already have an active virtual card!");
        }

        // Validate PIN
        if (request.getCardPin() == null || request.getCardPin().length() != 4) {
            throw new RuntimeException("Card PIN must be 4 digits!");
        }

        VirtualCard card = new VirtualCard();
        card.setUser(user);
        card.setCardType(request.getCardType().toUpperCase());
        card.setCardNumber(generateCardNumber(card.getCardType()));
        card.setCvv(String.format("%03d", new Random().nextInt(999)));
        card.setExpiryDate(generateExpiryDate());
        card.setCardPin(passwordEncoder.encode(request.getCardPin()));

        virtualCardRepository.save(card);

        return mapToResponse(card, user.getFullName());
    }

    public CardResponse getMyCard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        VirtualCard card = virtualCardRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("No active virtual card found!"));

        return mapToResponse(card, user.getFullName());
    }

    // Helper Methods
    private String generateCardNumber(String type) {
        Random rand = new Random();
        StringBuilder pan = new StringBuilder();
        // Visa starts with 4, Mastercard starts with 5
        pan.append(type.equals("VISA") ? "4" : "5");

        // Generate remaining 15 digits
        for (int i = 0; i < 15; i++) {
            pan.append(rand.nextInt(10));
        }
        return pan.toString();
    }

    private String generateExpiryDate() {
        // Expiry is exactly 3 years from today
        LocalDate expiry = LocalDate.now().plusYears(3);
        return expiry.format(DateTimeFormatter.ofPattern("MM/yy"));
    }

    private CardResponse mapToResponse(VirtualCard card, String fullName) {
        return CardResponse.builder()
                .cardNumber(card.getCardNumber())
                .expiryDate(card.getExpiryDate())
                .cvv(card.getCvv())
                .cardType(card.getCardType())
                .nameOnCard(fullName.toUpperCase())
                .isActive(card.isActive())
                .build();
    }
}
