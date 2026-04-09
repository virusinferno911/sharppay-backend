package com.virusinferno.sharppay.dto;

import lombok.Data;

@Data
public class UserRegistrationRequest {
    private String email;
    private String password;
    private String fullName;
    private String phoneNumber;
}
