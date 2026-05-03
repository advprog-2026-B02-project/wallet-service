package id.ac.ui.cs.advprog.bidmart.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WithdrawRequest {
    @Min(value = 10000, message = "Minimum withdrawal is 10000")
    private long amount;

    @NotBlank
    private String bankCode;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountName;
}
