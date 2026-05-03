package id.ac.ui.cs.advprog.bidmart.wallet.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuctionUnsoldEvent {
    private UUID eventId;
    private UUID auctionId;
    private LocalDateTime occurredAt;
}
