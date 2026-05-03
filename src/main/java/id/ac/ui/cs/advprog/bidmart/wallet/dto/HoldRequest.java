package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HoldRequest {
    private UUID userId;

    @NotNull
    private UUID auctionId;

    private UUID bidId;

    @Min(value = 1, message = "Hold amount must be positive")
    private long amount;
}
