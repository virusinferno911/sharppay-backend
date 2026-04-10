package com.virusinferno.sharppay.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UserSettingsRequest {
    private String transactionPin;
    private BigDecimal livenessTransferLimit;
}
