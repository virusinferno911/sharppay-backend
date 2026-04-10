package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

    @GetMapping("/resolve/{accountNumber}")
    public ResponseEntity<Map<String, String>> resolveAccount(
            @PathVariable String accountNumber,
            @RequestParam String bankCode) {

        if ("SHARP_PAY".equals(bankCode)) {
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new RuntimeException("Account not found in SharpPay"));

            return ResponseEntity.ok(Map.of("accountName", account.getUser().getFullName()));
        }

        throw new RuntimeException("External bank routing coming soon!");
    }
}