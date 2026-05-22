package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.service.IdempotencyService;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
    private final IdempotencyService idempotencyService;

    @PostMapping("/holds")
    public ResponseEntity<String> createHold(@Valid @RequestBody HoldRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest servletRequest) {
        return idempotencyService.execute(idempotencyKey, servletRequest.getRequestURI(),
                () -> ResponseEntity.status(HttpStatus.CREATED).body(walletService.createHold(request)));
    }

    @PostMapping("/holds/{holdId}/release")
    public ResponseEntity<String> releaseHold(@PathVariable UUID holdId,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest servletRequest) {
        return idempotencyService.execute(idempotencyKey, servletRequest.getRequestURI(),
                () -> ResponseEntity.ok(walletService.releaseHold(holdId)));
    }

    @PostMapping("/holds/{holdId}/capture")
    public ResponseEntity<String> captureHold(@PathVariable UUID holdId,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest servletRequest) {
        return idempotencyService.execute(idempotencyKey, servletRequest.getRequestURI(),
                () -> ResponseEntity.ok(walletService.captureHold(holdId)));
    }

    @PostMapping("/auctions/{auctionId}/settle")
    public ResponseEntity<String> settleAuction(
            @PathVariable UUID auctionId,
            @RequestParam(required = false) UUID sellerId,
            @Valid @RequestBody AuctionSettleRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest) {
        return idempotencyService.execute(idempotencyKey, servletRequest.getRequestURI(),
                () -> ResponseEntity.ok(walletService.settleAuction(auctionId, sellerId, request.getWinners())));
    }

    @PostMapping("/auctions/{auctionId}/release-all")
    public ResponseEntity<String> releaseAll(@PathVariable UUID auctionId,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest servletRequest) {
        return idempotencyService.execute(idempotencyKey, servletRequest.getRequestURI(),
                () -> ResponseEntity.ok(walletService.releaseAllHoldsForAuction(auctionId)));
    }
}
