package com.virusinferno.sharppay.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionHistoryResponse {
    private String transactionId;
    private String transactionType;
    private BigDecimal amount;
    private String description;
    private String status;
    private LocalDateTime date;
}
