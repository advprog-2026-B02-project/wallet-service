package id.ac.ui.cs.advprog.bidmart.wallet.service;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WalletService {
    WalletResponse getWallet(UUID userId);
    WalletResponse topUp(UUID userId, TopUpRequest request);
    WithdrawResponse withdraw(UUID userId, WithdrawRequest request);
    Page<TransactionResponse> getTransactionHistory(UUID userId, Pageable pageable);
    TransactionResponse getTransaction(UUID userId, UUID transactionId);
    WalletResponse resetWallet(UUID userId);

    HoldResponse createHold(HoldRequest request);
    HoldResponse releaseHold(UUID holdId);
    HoldResponse captureHold(UUID holdId);

    AuctionSettleResponse settleAuction(UUID auctionId, List<AuctionSettleRequest.WinnerEntry> winners);
    void releaseAllHoldsForAuction(UUID auctionId);
}
