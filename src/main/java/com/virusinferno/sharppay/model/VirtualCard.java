package com.virusinferno.sharppay.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "virtual_cards")
@Data
@NoArgsConstructor
public class VirtualCard {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String cardNumber; // 16 digits

    @Column(nullable = false)
    private String expiryDate; // MM/YY

    @Column(nullable = false)
    private String cvv; // 3 digits

    @Column(nullable = false)
    private String cardType; // VISA or MASTERCARD

    @Column(nullable = false)
    private String cardPin; // Securely Hashed

    private boolean isActive = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
