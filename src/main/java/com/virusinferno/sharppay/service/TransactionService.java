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

    // RETURN MAP SO FRONTEND CAN GET THE TRANSACTION ID FOR THE RECEIPT POPUP
    @Transactional
    public Map<String, Object> processTransfer(TransferRequest request, String senderEmail) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Transfer amount must be greater than zero!");
        }

        User senderUser = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender user not found!"));

        if (senderUser.getTransactionPin() == null) {
            throw new RuntimeException("Please set up your Transaction PIN in settings first!");
        }

        if (request.getTransactionPin() == null ||
                !passwordEncoder.matches(request.getTransactionPin(), senderUser.getTransactionPin())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        if (senderUser.getLivenessTransferLimit() != null && request.getAmount().compareTo(senderUser.getLivenessTransferLimit()) >= 0) {
            if (senderUser.getLivenessVerifiedAt() == null ||
                    senderUser.getLivenessVerifiedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
                throw new RuntimeException("High-value transfer blocked! Please perform a Facial Liveness Verification first.");
            }
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

        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));

        accountRepository.save(sender);
        accountRepository.save(receiver);

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

        // Returning Map so Frontend gets the ID
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Transfer successful!");
        response.put("transactionId", transaction.getTransactionId());

        return response;
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

        // PROPERLY MAPPING NAMES FOR FRONTEND
        return transactions.stream().map(tx -> {
            String sName = tx.getSenderAccount() != null ? tx.getSenderAccount().getUser().getFullName() : "System";
            String sAcct = tx.getSenderAccount() != null ? tx.getSenderAccount().getAccountNumber() : "";
            String rName = tx.getReceiverAccount() != null ? tx.getReceiverAccount().getUser().getFullName() : "User";
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
        }).collect(Collectors.toList());
    }

    public Transaction getTransactionReceipt(String transactionId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found!"));

        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found!"));

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