package id.ac.ui.cs.advprog.bidmart.wallet.event;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.AuctionSettleRequest;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletAuctionEventListener {

    private final WalletService walletService;

    @KafkaListener(
        topics = "auction.settled",
        groupId = "wallet-service",
        containerFactory = "auctionSettledListenerFactory"
    )
    public void onAuctionSettled(AuctionSettledEvent event) {
        log.info("Received auction.settled: auctionId={}, type={}, winners={}",
                event.getAuctionId(), event.getAuctionType(),
                event.getWinners() == null ? 0 : event.getWinners().size());

        List<AuctionSettledEvent.WinnerEntry> winners =
                event.getWinners() != null ? event.getWinners() : Collections.emptyList();

        if (winners.isEmpty()) {
            log.info("No winners in event for auctionId={} (type={}), skipping.",
                    event.getAuctionId(), event.getAuctionType());
            return;
        }

        List<AuctionSettleRequest.WinnerEntry> winnerEntries = winners.stream()
                .map(w -> new AuctionSettleRequest.WinnerEntry(w.getUserId(), w.getAmount()))
                .toList();

        walletService.settleAuction(event.getAuctionId(), event.getSellerId(), winnerEntries);
    }

    @KafkaListener(
        topics = "auction.unsold",
        groupId = "wallet-service",
        containerFactory = "auctionUnsoldListenerFactory"
    )
    public void onAuctionUnsold(AuctionUnsoldEvent event) {
        log.info("Received auction.unsold: auctionId={}", event.getAuctionId());
        walletService.releaseAllHoldsForAuction(event.getAuctionId());
    }
}
