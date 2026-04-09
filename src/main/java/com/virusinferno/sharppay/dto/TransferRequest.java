package com.virusinferno.sharppay.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferRequest {
    // We removed senderAccountNumber completely! The frontend cannot forge it anymore.
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String description;
}