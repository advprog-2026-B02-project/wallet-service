package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionReleaseAllResponse {
    private UUID auctionId;
    private List<AuctionSettleResponse.ReleasedEntry> released;
    private LocalDateTime releasedAt;
}
