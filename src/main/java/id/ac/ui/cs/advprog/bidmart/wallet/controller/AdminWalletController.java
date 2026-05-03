package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.TransactionResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.WalletResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/wallet")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    @Value("${internal.service-token}")
    private String serviceToken;

    @GetMapping("/users/{userId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID userId,
                                                    @RequestHeader(value = "X-Service-Token", required = false) String token) {
        verifyServiceToken(token);
        return ResponseEntity.ok(walletService.getWalletByUserIdForAdmin(userId));
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        verifyServiceToken(token);
        return ResponseEntity.ok(walletService.getTransactionHistoryForAdmin(userId, pageable));
    }

    private void verifyServiceToken(String token) {
        if (serviceToken == null || serviceToken.isBlank()) {
            return;
        }
        if (!serviceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
    }
}
