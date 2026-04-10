package com.virusinferno.sharppay.dto;

import lombok.Data;

@Data
public class CardCreateRequest {
    private String cardType; // "VISA" or "MASTERCARD"
    private String cardPin;  // 4-digit PIN
}
