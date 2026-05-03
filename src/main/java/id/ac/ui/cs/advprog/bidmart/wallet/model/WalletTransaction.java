package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    private String description;
    private UUID referenceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
