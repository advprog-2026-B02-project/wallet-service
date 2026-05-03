package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getWallet(Principal principal) {
        return ResponseEntity.ok(walletService.getWallet(resolveUserId(principal)));
    }

    @PostMapping("/me/topup")
    public ResponseEntity<WalletResponse> topUp(Principal principal,
                                                @Valid @RequestBody TopUpRequest request) {
        return ResponseEntity.ok(walletService.topUp(resolveUserId(principal), request));
    }

    @PostMapping("/me/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(Principal principal,
                                                     @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.withdraw(resolveUserId(principal), request));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            Principal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(walletService.getTransactionHistory(resolveUserId(principal), pageable));
    }

    @GetMapping("/me/transactions/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(Principal principal,
                                                              @PathVariable UUID transactionId) {
        return ResponseEntity.ok(walletService.getTransaction(resolveUserId(principal), transactionId));
    }

    @PostMapping("/me/reset")
    public ResponseEntity<WalletResponse> reset(Principal principal) {
        return ResponseEntity.ok(walletService.resetWallet(resolveUserId(principal)));
    }

    @PostMapping("/me/holds")
    public ResponseEntity<HoldResponse> createHold(Principal principal,
                                                   @Valid @RequestBody HoldRequest request) {
        request.setUserId(resolveUserId(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.createHold(request));
    }

    @PostMapping("/holds/{holdId}/release")
    public ResponseEntity<HoldResponse> releaseHold(@PathVariable UUID holdId) {
        return ResponseEntity.ok(walletService.releaseHold(holdId));
    }

    @PostMapping("/holds/{holdId}/capture")
    public ResponseEntity<HoldResponse> captureHold(@PathVariable UUID holdId) {
        return ResponseEntity.ok(walletService.captureHold(holdId));
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("User is not authenticated.");
        }
        String subject = principal.getName().trim();
        String source = "wallet-user-" + subject;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
