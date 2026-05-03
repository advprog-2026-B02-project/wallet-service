package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuctionSettleResponse {

    private UUID auctionId;
    private List<CapturedEntry> captured;
    private List<ReleasedEntry> released;
    private LocalDateTime settledAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CapturedEntry {
        private UUID holdId;
        private UUID userId;
        private long amount;
        private UUID transactionId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReleasedEntry {
        private UUID holdId;
        private UUID userId;
        private long amount;
    }
}
