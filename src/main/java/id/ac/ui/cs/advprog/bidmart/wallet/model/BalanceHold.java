package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "balance_holds",
        indexes = {
                @Index(name = "idx_balance_hold_wallet",
                        columnList = "wallet_id"),
                @Index(name = "idx_balance_hold_auction_status",
                        columnList = "auction_id, status"),
                @Index(name = "idx_balance_hold_user_auction_status",
                        columnList = "user_id, auction_id, status")
        }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BalanceHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "auction_id")
    private UUID auctionId;

    @Column(name = "bid_id")
    private UUID bidId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
