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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository; // NEW: We brought in the User Repository to look up the sender securely

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

        // 1. Find the sender securely using the email extracted directly from the JWT Token
        User senderUser = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender user not found!"));

        Account sender = accountRepository.findByUser(senderUser)
                .orElseThrow(() -> new RuntimeException("Sender account not found!"));

        // 2. Find the receiver
        Account receiver = accountRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver account not found!"));

        if (sender.getAccountNumber().equals(receiver.getAccountNumber())) {
            throw new RuntimeException("Cannot transfer money to your own account!");
        }

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds! Your balance is ₦" + sender.getBalance());
        }

        // 3. Move the money
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // 4. Generate the bank receipt
        Transaction transaction = new Transaction();
        transaction.setTransactionId("TXN-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSessionId("SES-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setSenderAccount(sender);
        transaction.setReceiverAccount(receiver);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus("COMPLETED");
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Internal P2P Transfer");

        transactionRepository.save(transaction);

        return "Transfer successful! You sent ₦" + request.getAmount() + " to " + receiver.getUser().getFullName() + ". Your new balance is ₦" + sender.getBalance();
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
}