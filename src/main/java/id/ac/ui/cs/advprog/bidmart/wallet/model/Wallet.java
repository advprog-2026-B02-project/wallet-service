package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Check(constraints = "available_balance >= 0 AND held_balance >= 0")
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

    @Column(nullable = false)
    @Builder.Default
    private boolean frozen = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private long version;

    public long getTotalBalance() {
        return availableBalance + heldBalance;
    }
}
