package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"auction.settled", "auction.unsold", "user.suspended"})
class WalletControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WalletService walletService;

    @Value("${internal.service-token:internal-secret-change-in-production}")
    String serviceToken;

    // ── health ────────────────────────────────────────────────────────────────

    @Test
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── internal: createHold ──────────────────────────────────────────────────

    @Test
    void createHold_returns201() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);

        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID()).amount(50_000L).build();

        mockMvc.perform(post("/internal/v1/wallet/holds")
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amount").value(50_000));
    }

    @Test
    void createHold_returns409WhenInsufficientBalance() throws Exception {
        UUID userId = topUpAndGetUserId(10_000L);

        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID()).amount(50_000L).build();

        mockMvc.perform(post("/internal/v1/wallet/holds")
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void createHold_requiresInternalHeaders() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID()).amount(50_000L).build();

        mockMvc.perform(post("/internal/v1/wallet/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/wallet/holds")
                        .header("X-Service-Token", serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHold_isIdempotentForSameKeyAndPath() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        UUID idempotencyKey = UUID.randomUUID();
        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID()).amount(50_000L).build();

        String firstResponse = mockMvc.perform(post("/internal/v1/wallet/holds")
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/internal/v1/wallet/holds")
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().json(firstResponse));
    }

    // ── internal: releaseHold ─────────────────────────────────────────────────

    @Test
    void releaseHold_returns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        HoldResponse hold = createHold(userId, 50_000L);

        mockMvc.perform(post("/internal/v1/wallet/holds/{id}/release", hold.getHoldId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/wallet/holds/{id}/release", hold.getHoldId())
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void releaseHold_idempotent_secondCallStillReturns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        HoldResponse hold = createHold(userId, 50_000L);
        walletService.releaseHold(hold.getHoldId());

        mockMvc.perform(post("/internal/v1/wallet/holds/{id}/release", hold.getHoldId())
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    // ── internal: captureHold ─────────────────────────────────────────────────

    @Test
    void captureHold_returns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        HoldResponse hold = createHold(userId, 50_000L);

        mockMvc.perform(post("/internal/v1/wallet/holds/{id}/capture", hold.getHoldId())
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void captureHold_idempotent_secondCallStillReturns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        HoldResponse hold = createHold(userId, 50_000L);
        walletService.captureHold(hold.getHoldId());

        mockMvc.perform(post("/internal/v1/wallet/holds/{id}/capture", hold.getHoldId())
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    // ── internal: settleAuction ───────────────────────────────────────────────

    @Test
    void settleAuction_returns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        UUID auctionId = UUID.randomUUID();
        createHoldForAuction(userId, auctionId, 50_000L);

        AuctionSettleRequest req = new AuctionSettleRequest(
                List.of(new AuctionSettleRequest.WinnerEntry(userId, 50_000L)));

        mockMvc.perform(post("/internal/v1/wallet/auctions/{id}/settle", auctionId)
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captured", hasSize(1)))
                .andExpect(jsonPath("$.released", hasSize(0)));
    }

    @Test
    void settleAuction_noHolds_returns200WithEmptyLists() throws Exception {
        UUID auctionId = UUID.randomUUID();
        UUID userId = topUpAndGetUserId(50_000L);

        AuctionSettleRequest req = new AuctionSettleRequest(
                List.of(new AuctionSettleRequest.WinnerEntry(userId, 50_000L)));

        mockMvc.perform(post("/internal/v1/wallet/auctions/{id}/settle", auctionId)
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captured", hasSize(0)))
                .andExpect(jsonPath("$.released", hasSize(0)));
    }

    // ── internal: releaseAll ──────────────────────────────────────────────────

    @Test
    void releaseAll_returns200() throws Exception {
        UUID userId = topUpAndGetUserId(100_000L);
        UUID auctionId = UUID.randomUUID();
        createHoldForAuction(userId, auctionId, 50_000L);

        mockMvc.perform(post("/internal/v1/wallet/auctions/{id}/release-all", auctionId)
                        .header("X-Service-Token", serviceToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released", hasSize(1)));
    }

    // ── admin ─────────────────────────────────────────────────────────────────

    @Test
    void adminGetWallet_returns200WithServiceToken() throws Exception {
        UUID userId = topUpAndGetUserId(50_000L);

        mockMvc.perform(get("/admin/v1/wallet/users/{id}", userId)
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.availableBalance").value(50_000));
    }

    @Test
    void adminGetWallet_returns401WithWrongToken() throws Exception {
        UUID userId = topUpAndGetUserId(50_000L);

        mockMvc.perform(get("/admin/v1/wallet/users/{id}", userId)
                        .header("X-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminGetWallet_returns401WithNoToken() throws Exception {
        UUID userId = topUpAndGetUserId(50_000L);

        mockMvc.perform(get("/admin/v1/wallet/users/{id}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminGetWallet_returns404IfWalletNotFound() throws Exception {
        mockMvc.perform(get("/admin/v1/wallet/users/{id}", UUID.randomUUID())
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminGetTransactions_returns200WithTopUpRecord() throws Exception {
        UUID userId = topUpAndGetUserId(50_000L);

        mockMvc.perform(get("/admin/v1/wallet/users/{id}/transactions", userId)
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[0].type").value("TOPUP"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID topUpAndGetUserId(long amount) {
        UUID userId = UUID.randomUUID();
        walletService.topUp(userId, new TopUpRequest(amount));
        return userId;
    }

    private HoldResponse createHold(UUID userId, long amount) {
        return walletService.createHold(HoldRequest.builder()
                .userId(userId).auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID()).amount(amount).build());
    }

    private void createHoldForAuction(UUID userId, UUID auctionId, long amount) {
        walletService.createHold(HoldRequest.builder()
                .userId(userId).auctionId(auctionId)
                .bidId(UUID.randomUUID()).amount(amount).build());
    }
}
