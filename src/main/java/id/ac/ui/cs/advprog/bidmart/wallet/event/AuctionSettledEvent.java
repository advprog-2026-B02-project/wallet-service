package id.ac.ui.cs.advprog.bidmart.wallet.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuctionSettledEvent {
    private UUID eventId;
    private UUID auctionId;
    private UUID listingId;
    private UUID sellerId;
    private String auctionType;
    private List<WinnerEntry> winners;
    private LocalDateTime occurredAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WinnerEntry {
        private UUID userId;
        private long amount;
    }
}
