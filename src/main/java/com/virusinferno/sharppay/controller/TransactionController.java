package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.DepositRequest;
import com.virusinferno.sharppay.dto.TransactionHistoryResponse;
import com.virusinferno.sharppay.dto.TransferRequest;
import com.virusinferno.sharppay.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(@RequestBody DepositRequest request) {
        String receipt = transactionService.processDeposit(request);
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestBody TransferRequest request, Principal principal) {
        // The Principal object hands over the email securely extracted from the Token!
        String receipt = transactionService.processTransfer(request, principal.getName());
        return ResponseEntity.ok(receipt);
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionHistoryResponse>> getHistory(@PathVariable String accountNumber) {
        List<TransactionHistoryResponse> history = transactionService.getAccountHistory(accountNumber);
        return ResponseEntity.ok(history);
    }
}