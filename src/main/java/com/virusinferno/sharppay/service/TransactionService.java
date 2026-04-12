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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber()).orElseThrow();
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setReceiverAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("DEPOSIT");
        transaction.setStatus("COMPLETED");
        transaction.setDescription("Wallet funding via bank transfer");
        transactionRepository.save(transaction);
        return "Deposit successful!";
    }

    @Transactional
    public Map<String, Object> processTransfer(TransferRequest request, String senderEmail) {
        User senderUser = userRepository.findByEmail(senderEmail).orElseThrow();

        if (request.getTransactionPin() == null || !passwordEncoder.matches(request.getTransactionPin(), senderUser.getTransactionPin())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        Account sender = accountRepository.findByUser(senderUser).orElseThrow();
        Account receiver = accountRepository.findByAccountNumber(request.getReceiverAccountNumber()).orElseThrow();

        if (sender.getBalance().compareTo(request.getAmount()) < 0) throw new RuntimeException("Insufficient funds!");

        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        Transaction transaction = new Transaction();
        transaction.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSenderAccount(sender);
        transaction.setReceiverAccount(receiver);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus("COMPLETED");
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Internal Transfer");
        transactionRepository.save(transaction);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Transfer successful!");
        response.put("transactionId", transaction.getTransactionId());
        return response;
    }

    // ==========================================
    // NEW: BILLS PAYMENT DEDUCTION LOGIC
    // ==========================================
    @Transactional
    public Map<String, Object> processBillPayment(String email, BigDecimal amount, String category, String billerId, String pin) {
        User user = userRepository.findByEmail(email).orElseThrow();

        if (pin == null || !passwordEncoder.matches(pin, user.getTransactionPin())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        Account account = accountRepository.findByUser(user).orElseThrow();
        if(account.getBalance().compareTo(amount) < 0) throw new RuntimeException("Insufficient balance for this bill!");

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0,8).toUpperCase());
        tx.setSenderAccount(account);
        tx.setAmount(amount);
        tx.setTransactionType(category.toUpperCase() + "_BILL");
        tx.setStatus("COMPLETED");
        tx.setDescription(category + " Payment - " + billerId);
        tx.setReceiverName(category + " Provider"); // Forces the name to display nicely
        transactionRepository.save(tx);

        Map<String, Object> res = new HashMap<>();
        res.put("message", "Bill paid successfully!");
        res.put("transactionId", tx.getTransactionId());
        return res;
    }

    public List<TransactionHistoryResponse> getMyTransactions(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Account account = accountRepository.findByUser(user).orElseThrow();
        return getAccountHistory(account.getAccountNumber());
    }

    public List<TransactionHistoryResponse> getAccountHistory(String accountNumber) {
        List<Transaction> transactions = transactionRepository
                .findBySenderAccount_AccountNumberOrReceiverAccount_AccountNumberOrderByCreatedAtDesc(accountNumber, accountNumber);

        return transactions.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // ==========================================
    // FIXED: RECEIPT NOW RETURNS THE DTO WITH EXACT NAMES
    // ==========================================
    public TransactionHistoryResponse getTransactionReceipt(String transactionId, String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Account account = accountRepository.findByUser(user).orElseThrow();
        Transaction transaction = transactionRepository.findByTransactionId(transactionId).orElseThrow();

        boolean isSender = transaction.getSenderAccount() != null && transaction.getSenderAccount().getAccountNumber().equals(account.getAccountNumber());
        boolean isReceiver = transaction.getReceiverAccount() != null && transaction.getReceiverAccount().getAccountNumber().equals(account.getAccountNumber());

        if (!isSender && !isReceiver) throw new RuntimeException("Unauthorized receipt access.");

        return mapToDto(transaction);
    }

    // Centralized mapper to ensure names NEVER drop
    private TransactionHistoryResponse mapToDto(Transaction tx) {
        String sName = tx.getSenderName() != null ? tx.getSenderName() : (tx.getSenderAccount() != null ? tx.getSenderAccount().getUser().getFullName() : "System");
        String sAcct = tx.getSenderAccount() != null ? tx.getSenderAccount().getAccountNumber() : "";
        String rName = tx.getReceiverName() != null ? tx.getReceiverName() : (tx.getReceiverAccount() != null ? tx.getReceiverAccount().getUser().getFullName() : "External Entity");
        String rAcct = tx.getReceiverAccount() != null ? tx.getReceiverAccount().getAccountNumber() : "";

        return TransactionHistoryResponse.builder()
                .transactionId(tx.getTransactionId())
                .transactionType(tx.getTransactionType())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .status(tx.getStatus())
                .date(tx.getCreatedAt())
                .senderName(sName)
                .senderAccountNumber(sAcct)
                .receiverName(rName)
                .receiverAccountNumber(rAcct)
                .build();
    }
}