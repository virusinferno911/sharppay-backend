package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.BillPaymentRequest;
import com.virusinferno.sharppay.service.BillPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    @PostMapping("/pay")
    public ResponseEntity<String> payBill(@RequestBody BillPaymentRequest request, Principal principal) {
        String receipt = billPaymentService.payBill(principal.getName(), request);
        return ResponseEntity.ok(receipt);
    }
}
