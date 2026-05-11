package id.ac.ui.cs.advprog.bidmart.wallet.event;

import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletUserEventListener {

    private final WalletService walletService;

    @KafkaListener(
        topics = "user.suspended",
        groupId = "wallet-service",
        containerFactory = "userSuspendedListenerFactory"
    )
    public void onUserSuspended(UserSuspendedEvent event) {
        if (event.getUserId() == null) {
            log.warn("Ignoring user.suspended event without userId: eventId={}", event.getEventId());
            return;
        }
        walletService.freezeWallet(event.getUserId(), event.getReason());
    }
}
