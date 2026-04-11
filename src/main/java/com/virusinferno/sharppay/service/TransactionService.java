package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.dto.DepositRequest;
import com.virusinferno.sharppay.dto.TransactionHistoryResponse;
import com.virusinferno.sharppay.dto.TransferRequest;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String processDeposit(DepositRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Deposit amount must be greater than zero!");
        }

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSessionId("SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setReceiverAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("DEPOSIT");
        transaction.setStatus("COMPLETED");
        transaction.setDescription("Wallet funding via bank transfer");

        transactionRepository.save(transaction);

        return "Deposit successful! New Balance: ₦" + account.getBalance();
    }

    @Transactional
    public String processTransfer(TransferRequest request, String senderEmail) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Transfer amount must be greater than zero!");
        }

        User senderUser = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender user not found!"));

        // ==========================================
        // SECURITY CHECK 1: VERIFY TRANSACTION PIN
        // ==========================================
        if (senderUser.getTransactionPin() == null) {
            throw new RuntimeException("Please set up your Transaction PIN in settings first!");
        }

        if (request.getTransactionPin() == null ||
                !passwordEncoder.matches(request.getTransactionPin(), senderUser.getTransactionPin())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        // ==========================================
        // SECURITY CHECK 2: THE BIOMETRIC LIMIT TRIGGER
        // ==========================================
        if (senderUser.getLivenessTransferLimit() != null && request.getAmount().compareTo(senderUser.getLivenessTransferLimit()) >= 0) {

            // Did they verify their face in the last 5 minutes?
            if (senderUser.getLivenessVerifiedAt() == null ||
                    senderUser.getLivenessVerifiedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
                throw new RuntimeException("High-value transfer blocked! Please perform a Facial Liveness Verification first.");
            }

            // If they pass, lock the door behind them so they must verify again next time
            senderUser.setLivenessVerifiedAt(null);
            userRepository.save(senderUser);
        }

        Account sender = accountRepository.findByUser(senderUser)
                .orElseThrow(() -> new RuntimeException("Sender account not found!"));

        Account receiver = accountRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver account not found!"));

        if (sender.getAccountNumber().equals(receiver.getAccountNumber())) {
            throw new RuntimeException("Cannot transfer money to your own account!");
        }

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds! Your balance is ₦" + sender.getBalance());
        }

        // Move the money
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // Generate the bank receipt
        Transaction transaction = new Transaction();
        transaction.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSessionId("SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSenderAccount(sender);
        transaction.setReceiverAccount(receiver);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus("COMPLETED");
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Internal P2P Transfer");

        transactionRepository.save(transaction);

        return "Transfer successful! You sent ₦" + request.getAmount() + " to " + receiver.getUser().getFullName() + ". Your new balance is ₦" + sender.getBalance();
    }

    public List<TransactionHistoryResponse> getMyTransactions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        return getAccountHistory(account.getAccountNumber());
    }

    public List<TransactionHistoryResponse> getAccountHistory(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        List<Transaction> transactions = transactionRepository
                .findBySenderAccount_AccountNumberOrReceiverAccount_AccountNumberOrderByCreatedAtDesc(
                        accountNumber, accountNumber);

        return transactions.stream().map(tx -> TransactionHistoryResponse.builder()
                .transactionId(tx.getTransactionId())
                .transactionType(tx.getTransactionType())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .status(tx.getStatus())
                .date(tx.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }

    // ==========================================
    // PHASE 3: FETCH SINGLE TRANSACTION RECEIPT
    // ==========================================
    public Transaction getTransactionReceipt(String transactionId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found!"));

        // Security Check: Are they the sender or the receiver?
        boolean isSender = transaction.getSenderAccount() != null &&
                transaction.getSenderAccount().getAccountNumber().equals(account.getAccountNumber());
        boolean isReceiver = transaction.getReceiverAccount() != null &&
                transaction.getReceiverAccount().getAccountNumber().equals(account.getAccountNumber());

        if (!isSender && !isReceiver) {
            throw new RuntimeException("Unauthorized: You do not have permission to view this receipt.");
        }

        return transaction;
    }
}