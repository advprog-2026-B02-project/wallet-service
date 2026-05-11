package id.ac.ui.cs.advprog.bidmart.wallet.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSuspendedEvent {
    private UUID eventId;
    private UUID userId;
    private String reason;
    private LocalDateTime occurredAt;
}
