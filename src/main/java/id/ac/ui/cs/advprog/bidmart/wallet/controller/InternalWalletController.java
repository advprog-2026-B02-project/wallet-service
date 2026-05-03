package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wallet")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    @PostMapping("/holds")
    public ResponseEntity<HoldResponse> createHold(@Valid @RequestBody HoldRequest request) {
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

    @PostMapping("/auctions/{auctionId}/settle")
    public ResponseEntity<AuctionSettleResponse> settleAuction(
            @PathVariable UUID auctionId,
            @Valid @RequestBody AuctionSettleRequest request) {
        return ResponseEntity.ok(walletService.settleAuction(auctionId, request.getWinners()));
    }

    @PostMapping("/auctions/{auctionId}/release-all")
    public ResponseEntity<Void> releaseAll(@PathVariable UUID auctionId) {
        walletService.releaseAllHoldsForAuction(auctionId);
        return ResponseEntity.ok().build();
    }
}
