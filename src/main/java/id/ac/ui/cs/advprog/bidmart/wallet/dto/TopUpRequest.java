package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import jakarta.validation.constraints.Min;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TopUpRequest {
    @Min(value = 1, message = "Top-up amount must be positive")
    private long amount;
}
