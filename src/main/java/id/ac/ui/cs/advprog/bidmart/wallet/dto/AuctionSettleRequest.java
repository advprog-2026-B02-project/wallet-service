package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuctionSettleRequest {

    @NotEmpty
    private List<WinnerEntry> winners;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WinnerEntry {
        private UUID userId;
        private long captureAmount;
    }
}
