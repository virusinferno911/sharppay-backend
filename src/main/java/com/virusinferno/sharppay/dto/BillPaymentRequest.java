package com.virusinferno.sharppay.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BillPaymentRequest {
    private String billType; // e.g., "AIRTIME", "DSTV", "ELECTRICITY"
    private String targetNumber; // Phone number or Meter number
    private BigDecimal amount;
    private String transactionPin; // Security first!
}