package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HoldResponse {
    private UUID holdId;
    private UUID userId;
    private UUID auctionId;
    private long amount;
    private String status;
    private LocalDateTime createdAt;
}
