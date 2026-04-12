package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.DepositRequest;
import com.virusinferno.sharppay.dto.TransactionHistoryResponse;
import com.virusinferno.sharppay.dto.TransferRequest;
import com.virusinferno.sharppay.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
        return ResponseEntity.ok(transactionService.processDeposit(request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransferRequest request, Principal principal) {
        return ResponseEntity.ok(transactionService.processTransfer(request, principal.getName()));
    }

    // ==========================================
    // NEW: BILLS ENDPOINT
    // ==========================================
    @PostMapping("/bills")
    public ResponseEntity<Map<String, Object>> payBill(@RequestBody Map<String, String> request, Principal principal) {
        BigDecimal amount = new BigDecimal(request.get("amount"));
        Map<String, Object> receipt = transactionService.processBillPayment(
                principal.getName(), amount, request.get("category"), request.get("billerId"), request.get("pin"));
        return ResponseEntity.ok(receipt);
    }

    @GetMapping
    public ResponseEntity<List<TransactionHistoryResponse>> getMyTransactions(Principal principal) {
        return ResponseEntity.ok(transactionService.getMyTransactions(principal.getName()));
    }

    // FIXED: Now returns TransactionHistoryResponse DTO!
    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<TransactionHistoryResponse> getReceipt(@PathVariable String transactionId, Principal principal) {
        return ResponseEntity.ok(transactionService.getTransactionReceipt(transactionId, principal.getName()));
    }
}