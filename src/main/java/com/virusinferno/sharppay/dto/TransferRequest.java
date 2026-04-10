package com.virusinferno.sharppay.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferRequest {
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String description;

    // NEW: The user must provide their PIN to move money
    private String transactionPin;
}