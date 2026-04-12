package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.BillPaymentRequest;
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
        return ResponseEntity.ok(transactionService.processDeposit(request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransferRequest request, Principal principal) {
        return ResponseEntity.ok(transactionService.processTransfer(request, principal.getName()));
    }

    // FIXED: Perfectly matches your BillPaymentRequest DTO!
    @PostMapping("/bills")
    public ResponseEntity<Map<String, Object>> payBill(@RequestBody BillPaymentRequest request, Principal principal) {
        Map<String, Object> receipt = transactionService.processBillPayment(
                principal.getName(),
                request.getAmount(),
                request.getBillType(),
                request.getTargetNumber(),
                request.getTransactionPin());
        return ResponseEntity.ok(receipt);
    }

    @GetMapping
    public ResponseEntity<List<TransactionHistoryResponse>> getMyTransactions(Principal principal) {
        return ResponseEntity.ok(transactionService.getMyTransactions(principal.getName()));
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionHistoryResponse>> getHistory(@PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getAccountHistory(accountNumber));
    }

    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<TransactionHistoryResponse> getReceipt(@PathVariable String transactionId, Principal principal) {
        return ResponseEntity.ok(transactionService.getTransactionReceipt(transactionId, principal.getName()));
    }
}