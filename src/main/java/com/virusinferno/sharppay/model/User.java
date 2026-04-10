package com.virusinferno.sharppay.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;
    private String phoneNumber;

    @Column(nullable = false)
    private String kycStatus = "PENDING";

    @Column(nullable = false)
    private String role = "USER";

    private String faceCollectionId;
    private String idS3FrontKey;
    private String idS3BackKey;

    private boolean isActive = true;

    // ==========================================
    // SHARPPAY V2: STEP-UP SECURITY FEATURES
    // ==========================================

    @Column(name = "transaction_pin")
    private String transactionPin;

    @Column(name = "liveness_transfer_limit")
    private BigDecimal livenessTransferLimit;

    @Column(name = "trusted_device_id")
    private String trustedDeviceId;

    @Column(name = "selfie_s3_key")
    private String selfieS3Key;

    @Column(name = "liveness_verified_at")
    private LocalDateTime livenessVerifiedAt;

    // ==========================================

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}