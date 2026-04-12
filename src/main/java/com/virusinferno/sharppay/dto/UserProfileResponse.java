package com.virusinferno.sharppay.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class UserProfileResponse {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String kycStatus;
    private String accountNumber;
    private BigDecimal balance;

    // This is the only new line!
    private BigDecimal livenessTransferLimit;
}