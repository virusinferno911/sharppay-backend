package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.BillPaymentRequest;
import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.Transaction;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.TransactionRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillPaymentService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String payBill(String email, BillPaymentRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero!");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // 1. PIN Security Check
        if (user.getTransactionPin() == null) {
            throw new RuntimeException("Please set up your Transaction PIN in settings first!");
        }
        if (request.getTransactionPin() == null || !passwordEncoder.matches(request.getTransactionPin(), user.getTransactionPin())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        // 2. Balance Check
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds! Your balance is ₦" + account.getBalance());
        }

        // 3. Deduct from SharpPay Wallet
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        // 4. Generate the Receipt
        String reference = "BP-" + request.getBillType().toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Transaction transaction = new Transaction();
        transaction.setTransactionId(reference);
        transaction.setSessionId("SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSenderAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("BILL_PAYMENT");
        transaction.setStatus("COMPLETED");
        transaction.setDescription("Paid " + request.getBillType() + " for " + request.getTargetNumber());

        transactionRepository.save(transaction);

        return "Successfully paid ₦" + request.getAmount() + " for " + request.getBillType() + " (" + request.getTargetNumber() + "). New balance: ₦" + account.getBalance();
    }
}
