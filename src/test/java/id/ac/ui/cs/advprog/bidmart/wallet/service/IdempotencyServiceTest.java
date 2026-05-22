package id.ac.ui.cs.advprog.bidmart.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.bidmart.wallet.model.IdempotencyRecord;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private IdempotencyRecordRepository repository;
    private ObjectMapper objectMapper;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRecordRepository.class);
        objectMapper = mock(ObjectMapper.class);
        service = new IdempotencyService(repository, objectMapper);
    }

    @Test
    void execute_returnsStoredResponseWhenRecordExists() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .recordKey("key:/internal/v1/wallet/holds")
                .idempotencyKey("key")
                .requestPath("/internal/v1/wallet/holds")
                .responseStatus(HttpStatus.CREATED.value())
                .responseBody("{\"status\":\"ACTIVE\"}")
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.findById(record.getRecordKey())).thenReturn(Optional.of(record));

        ResponseEntity<String> response = service.execute("key", "/internal/v1/wallet/holds", () -> {
            throw new AssertionError("action should not run for an idempotency hit");
        });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"ACTIVE\"}");
        verifyNoInteractions(objectMapper);
    }

    @Test
    void execute_storesSerializedResponseForFirstRequest() throws Exception {
        Object body = Map.of("holdId", "hold-1");
        when(repository.findById("key:/internal/v1/wallet/holds")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(body)).thenReturn("{\"holdId\":\"hold-1\"}");

        ResponseEntity<String> response = service.execute("key", "/internal/v1/wallet/holds",
                () -> ResponseEntity.status(HttpStatus.CREATED)
                        .header("X-Test", "yes")
                        .body(body));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Test")).isEqualTo("yes");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"holdId\":\"hold-1\"}");
        verify(repository).save(argThat(record ->
                record.getRecordKey().equals("key:/internal/v1/wallet/holds")
                        && record.getResponseStatus() == HttpStatus.CREATED.value()
                        && record.getResponseBody().equals("{\"holdId\":\"hold-1\"}")));
    }

    @Test
    void execute_storesEmptyJsonObjectWhenResponseBodyIsNull() {
        when(repository.findById("key:/internal/v1/wallet/holds")).thenReturn(Optional.empty());

        ResponseEntity<String> response = service.execute("key", "/internal/v1/wallet/holds",
                () -> ResponseEntity.accepted().body(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo("{}");
        verifyNoInteractions(objectMapper);
        verify(repository).save(argThat(record -> record.getResponseBody().equals("{}")));
    }

    @Test
    void execute_throwsWhenResponseBodyCannotBeSerialized() throws Exception {
        Object body = new Object();
        when(repository.findById("key:/internal/v1/wallet/holds")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(body)).thenThrow(new JsonProcessingException("boom") { });

        assertThatThrownBy(() -> service.execute("key", "/internal/v1/wallet/holds",
                () -> ResponseEntity.ok(body)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize idempotent response");
        verify(repository, never()).save(any());
    }
}
