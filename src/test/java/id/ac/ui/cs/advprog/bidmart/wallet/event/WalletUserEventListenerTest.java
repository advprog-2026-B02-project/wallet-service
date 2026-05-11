package id.ac.ui.cs.advprog.bidmart.wallet.event;

import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletUserEventListenerTest {

    private WalletService walletService;
    private WalletUserEventListener listener;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        listener = new WalletUserEventListener(walletService);
    }

    @Test
    void onUserSuspended_freezesWallet() {
        UUID userId = UUID.randomUUID();

        listener.onUserSuspended(UserSuspendedEvent.builder()
                .userId(userId)
                .reason("policy")
                .build());

        verify(walletService).freezeWallet(userId, "policy");
    }

    @Test
    void onUserSuspended_ignoresMissingUserId() {
        listener.onUserSuspended(UserSuspendedEvent.builder()
                .eventId(UUID.randomUUID())
                .reason("policy")
                .build());

        verifyNoInteractions(walletService);
    }
}
