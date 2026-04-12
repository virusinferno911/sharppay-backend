package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.CardCreateRequest;
import com.virusinferno.sharppay.dto.CardResponse;
import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.Transaction;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.model.VirtualCard;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.TransactionRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import com.virusinferno.sharppay.repository.VirtualCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VirtualCardService {

    private final VirtualCardRepository virtualCardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public CardResponse createCard(String email, CardCreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        Optional<VirtualCard> existingCard = virtualCardRepository.findByUser(user);
        if (existingCard.isPresent() && !"DISABLED".equals(existingCard.get().getStatus())) {
            throw new RuntimeException("You already have an active or frozen virtual card!");
        }

        if (request.getCardPin() == null || request.getCardPin().length() != 4) {
            throw new RuntimeException("Card PIN must be 4 digits!");
        }

        // ==========================================
        // DEDUCT CARD CREATION FEE (₦1,000)
        // ==========================================
        BigDecimal cardFee = new BigDecimal("1000.00");
        if (account.getBalance().compareTo(cardFee) < 0) {
            throw new RuntimeException("Insufficient balance to create a card. You need ₦1,000.");
        }

        account.setBalance(account.getBalance().subtract(cardFee));
        accountRepository.save(account);

        // Record the fee in Transaction History
        Transaction feeTx = new Transaction();
        feeTx.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        feeTx.setSessionId("SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        feeTx.setSenderAccount(account);
        feeTx.setAmount(cardFee);
        feeTx.setTransactionType("CARD_FEE");
        feeTx.setStatus("COMPLETED");
        feeTx.setDescription("Virtual Card Creation Fee");
        transactionRepository.save(feeTx);

        // Create the card
        VirtualCard card = existingCard.isPresent() ? existingCard.get() : new VirtualCard();
        card.setUser(user);
        card.setCardType(request.getCardType() != null ? request.getCardType().toUpperCase() : "VIRTUAL");
        card.setCardNumber(generateCardNumber(card.getCardType()));
        card.setCvv(String.format("%03d", new Random().nextInt(999)));
        card.setExpiryDate(generateExpiryDate());
        card.setCardPin(passwordEncoder.encode(request.getCardPin()));
        card.setStatus("ACTIVE");

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

    @Transactional
    public String updateCardStatus(String email, String newStatus) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        VirtualCard card = virtualCardRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("No virtual card found for this account."));

        if ("DISABLED".equals(card.getStatus()) && !"DISABLED".equals(newStatus)) {
            throw new RuntimeException("This card is permanently disabled. You must create a new one.");
        }

        card.setStatus(newStatus.toUpperCase());
        virtualCardRepository.save(card);

        if ("FROZEN".equals(newStatus)) return "Your virtual card has been temporarily frozen.";
        if ("ACTIVE".equals(newStatus)) return "Your virtual card has been unfrozen and is ready to use.";
        return "Your virtual card has been permanently disabled.";
    }

    private String generateCardNumber(String type) {
        Random rand = new Random();
        StringBuilder pan = new StringBuilder();
        pan.append(type.equals("VISA") ? "4" : "5");
        for (int i = 0; i < 15; i++) {
            pan.append(rand.nextInt(10));
        }
        return pan.toString();
    }

    private String generateExpiryDate() {
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
                .status(card.getStatus())
                .build();
    }
}