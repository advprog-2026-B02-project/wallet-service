package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private long availableBalance;

    @Column(nullable = false)
    private long heldBalance;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private long version;

    public long getTotalBalance() {
        return availableBalance + heldBalance;
    }
}
