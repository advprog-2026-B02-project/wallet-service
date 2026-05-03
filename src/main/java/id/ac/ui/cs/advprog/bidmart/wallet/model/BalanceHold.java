package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_holds")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BalanceHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private UUID userId;

    private UUID auctionId;
    private UUID bidId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
