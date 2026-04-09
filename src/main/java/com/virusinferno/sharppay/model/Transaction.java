package com.virusinferno.sharppay.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false, length = 30)
    private String transactionId;

    @Column(unique = true, nullable = false, length = 30)
    private String sessionId;

    // The account sending the money (Can be null for external deposits)
    @ManyToOne
    @JoinColumn(name = "sender_account_id")
    private Account senderAccount;

    // The account receiving the money
    @ManyToOne
    @JoinColumn(name = "receiver_account_id")
    private Account receiverAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String transactionType; // e.g., "DEPOSIT", "TRANSFER"

    @Column(nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String reference;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}