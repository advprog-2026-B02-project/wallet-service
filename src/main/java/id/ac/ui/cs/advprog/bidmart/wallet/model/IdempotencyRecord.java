package id.ac.ui.cs.advprog.bidmart.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    private String recordKey;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestPath;

    @Column(nullable = false)
    private int responseStatus;

    @Lob
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
