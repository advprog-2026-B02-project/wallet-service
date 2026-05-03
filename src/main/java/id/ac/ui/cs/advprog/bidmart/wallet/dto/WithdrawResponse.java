package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WithdrawResponse {
    private UUID transactionId;
    private long amount;
    private long fee;
    private long netAmount;
    private String status;
    private LocalDateTime estimatedCompletion;
}
