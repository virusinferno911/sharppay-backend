package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.DepositRequest;
import com.virusinferno.sharppay.dto.TransactionHistoryResponse;
import com.virusinferno.sharppay.dto.TransferRequest;
import com.virusinferno.sharppay.model.Transaction;
import com.virusinferno.sharppay.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

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

    // FIXED: Now returns Map<String, Object> so React gets the transactionId for the popup!
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransferRequest request, Principal principal) {
        Map<String, Object> receipt = transactionService.processTransfer(request, principal.getName());
        return ResponseEntity.ok(receipt);
    }

    @GetMapping
    public ResponseEntity<List<TransactionHistoryResponse>> getMyTransactions(Principal principal) {
        List<TransactionHistoryResponse> history = transactionService.getMyTransactions(principal.getName());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionHistoryResponse>> getHistory(@PathVariable String accountNumber) {
        List<TransactionHistoryResponse> history = transactionService.getAccountHistory(accountNumber);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<Transaction> getReceipt(@PathVariable String transactionId, Principal principal) {
        Transaction receipt = transactionService.getTransactionReceipt(transactionId, principal.getName());
        return ResponseEntity.ok(receipt);
    }
}