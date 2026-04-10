package com.virusinferno.sharppay.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    public void sendOtpEmail(String toEmail, String otpCode) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resendApiKey);

        String htmlBody = "<div style='font-family: Arial, sans-serif; text-align: center; padding: 20px;'>" +
                "<h2 style='color: #e11d48;'>Welcome to SharpPay!</h2>" +
                "<p>Your secure verification code is:</p>" +
                "<h1 style='letter-spacing: 5px; color: #333;'>" + otpCode + "</h1>" +
                "<p>This code will expire in 10 minutes. Do not share it with anyone.</p></div>";

        // Note: Since you verified virusinferno.xyz on Namecheap, you can use support@virusinferno.xyz or onboarding@virusinferno.xyz here!
        Map<String, Object> body = Map.of(
                "from", "SharpPay <onboarding@virusinferno.xyz>",
                "to", new String[]{toEmail},
                "subject", "Your SharpPay Verification Code",
                "html", htmlBody
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);
            System.out.println("OTP Email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.out.println("Failed to send OTP Email: " + e.getMessage());
        }
    }
}