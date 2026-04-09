package com.virusinferno.sharppay.repository;

import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);

    // NEW: Find a wallet belonging to a specific user
    Optional<Account> findByUser(User user);
}