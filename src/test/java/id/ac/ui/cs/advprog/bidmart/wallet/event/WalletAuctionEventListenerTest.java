package id.ac.ui.cs.advprog.bidmart.wallet.event;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.AuctionSettleRequest;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletAuctionEventListenerTest {

    private WalletService walletService;
    private WalletAuctionEventListener listener;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        listener = new WalletAuctionEventListener(walletService);
    }

    @Test
    void onAuctionSettled_skipsWhenWinnersAreNull() {
        AuctionSettledEvent event = AuctionSettledEvent.builder()
                .auctionId(UUID.randomUUID())
                .auctionType("OPEN")
                .winners(null)
                .build();

        listener.onAuctionSettled(event);

        verifyNoInteractions(walletService);
    }

    @Test
    void onAuctionSettled_skipsWhenWinnersAreEmpty() {
        AuctionSettledEvent event = AuctionSettledEvent.builder()
                .auctionId(UUID.randomUUID())
                .auctionType("OPEN")
                .winners(Collections.emptyList())
                .build();

        listener.onAuctionSettled(event);

        verifyNoInteractions(walletService);
    }

    @Test
    void onAuctionSettled_mapsWinnersAndCallsSettleAuction() {
        UUID auctionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuctionSettledEvent event = AuctionSettledEvent.builder()
                .auctionId(auctionId)
                .auctionType("OPEN")
                .winners(List.of(AuctionSettledEvent.WinnerEntry.builder()
                        .userId(userId)
                        .amount(75_000L)
                        .build()))
                .build();
        ArgumentCaptor<List<AuctionSettleRequest.WinnerEntry>> winnersCaptor = ArgumentCaptor.forClass(List.class);

        listener.onAuctionSettled(event);

        verify(walletService).settleAuction(org.mockito.ArgumentMatchers.eq(auctionId), winnersCaptor.capture());
        assertThat(winnersCaptor.getValue()).hasSize(1);
        assertThat(winnersCaptor.getValue().getFirst().getUserId()).isEqualTo(userId);
        assertThat(winnersCaptor.getValue().getFirst().getCaptureAmount()).isEqualTo(75_000L);
    }

    @Test
    void onAuctionUnsold_releasesAllHoldsForAuction() {
        UUID auctionId = UUID.randomUUID();
        AuctionUnsoldEvent event = AuctionUnsoldEvent.builder().auctionId(auctionId).build();

        listener.onAuctionUnsold(event);

        verify(walletService).releaseAllHoldsForAuction(auctionId);
    }
}
