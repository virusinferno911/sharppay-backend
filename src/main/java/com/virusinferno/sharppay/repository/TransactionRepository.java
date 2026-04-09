package com.virusinferno.sharppay.repository;

import com.virusinferno.sharppay.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByTransactionId(String transactionId);

    // Custom query to find all history for a specific account (newest first)
    List<Transaction> findBySenderAccount_AccountNumberOrReceiverAccount_AccountNumberOrderByCreatedAtDesc(
            String senderAccountNumber,
            String receiverAccountNumber
    );
}