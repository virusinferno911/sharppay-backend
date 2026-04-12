package com.virusinferno.sharppay.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferRequest {
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String description;

    // The user must provide their PIN to move money
    private String transactionPin;

    // NEW: Added to prevent 400 Bad Request when React sends external bank info
    private String bankCode;
}