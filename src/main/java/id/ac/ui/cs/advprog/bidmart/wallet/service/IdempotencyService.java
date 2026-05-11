package id.ac.ui.cs.advprog.bidmart.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.bidmart.wallet.model.IdempotencyRecord;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResponseEntity<String> execute(String idempotencyKey,
                                          String requestPath,
                                          Supplier<ResponseEntity<?>> action) {
        String recordKey = idempotencyKey + ":" + requestPath;
        return repository.findById(recordKey)
                .map(record -> ResponseEntity.status(record.getResponseStatus())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(record.getResponseBody()))
                .orElseGet(() -> executeAndStore(recordKey, idempotencyKey, requestPath, action));
    }

    private ResponseEntity<String> executeAndStore(String recordKey,
                                                   String idempotencyKey,
                                                   String requestPath,
                                                   Supplier<ResponseEntity<?>> action) {
        ResponseEntity<?> response = action.get();
        String responseBody = serialize(response.getBody());

        repository.save(IdempotencyRecord.builder()
                .recordKey(recordKey)
                .idempotencyKey(idempotencyKey)
                .requestPath(requestPath)
                .responseStatus(response.getStatusCode().value())
                .responseBody(responseBody)
                .createdAt(LocalDateTime.now())
                .build());

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    private String serialize(Object body) {
        if (body == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }
}
