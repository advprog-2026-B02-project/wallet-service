package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private String type;
    private long amount;
    private String description;
    private UUID referenceId;
    private long balanceAfter;
    private LocalDateTime createdAt;
}
