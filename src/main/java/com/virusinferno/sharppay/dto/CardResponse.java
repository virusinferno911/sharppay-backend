package com.virusinferno.sharppay.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CardResponse {
    private String cardNumber;
    private String expiryDate;
    private String cvv;
    private String cardType;
    private String nameOnCard;

    // --- PHASE 2: The updated status field ---
    private String status;
}