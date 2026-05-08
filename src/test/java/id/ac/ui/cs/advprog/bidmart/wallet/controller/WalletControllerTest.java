package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.HoldRequest;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.HoldResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.TopUpRequest;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.TransactionResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.WalletResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.WithdrawRequest;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.WithdrawResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletControllerTest {

    private WalletService walletService;
    private WalletController controller;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        controller = new WalletController(walletService);
    }

    @Test
    void getWallet_returnsWalletForResolvedUser() {
        Principal principal = principal("alice");
        UUID expectedUserId = resolvedUserId("alice");
        WalletResponse response = WalletResponse.builder().userId(expectedUserId).availableBalance(50_000L).build();
        when(walletService.getWallet(expectedUserId)).thenReturn(response);

        ResponseEntity<WalletResponse> result = controller.getWallet(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).getWallet(expectedUserId);
    }

    @Test
    void topUp_usesResolvedUserId() {
        Principal principal = principal("bob");
        UUID expectedUserId = resolvedUserId("bob");
        TopUpRequest request = new TopUpRequest(20_000L);
        WalletResponse response = WalletResponse.builder().userId(expectedUserId).availableBalance(20_000L).build();
        when(walletService.topUp(expectedUserId, request)).thenReturn(response);

        ResponseEntity<WalletResponse> result = controller.topUp(principal, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).topUp(expectedUserId, request);
    }

    @Test
    void withdraw_returnsCreatedResponse() {
        Principal principal = principal("carol");
        UUID expectedUserId = resolvedUserId("carol");
        WithdrawRequest request = WithdrawRequest.builder()
                .amount(10_000L)
                .bankCode("BCA")
                .accountNumber("123")
                .accountName("Carol")
                .build();
        WithdrawResponse response = WithdrawResponse.builder().status("PROCESSING").build();
        when(walletService.withdraw(expectedUserId, request)).thenReturn(response);

        ResponseEntity<WithdrawResponse> result = controller.withdraw(principal, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).withdraw(expectedUserId, request);
    }

    @Test
    void getTransactions_usesResolvedUserIdAndPageable() {
        Principal principal = principal("dave");
        UUID expectedUserId = resolvedUserId("dave");
        PageRequest pageable = PageRequest.of(0, 20);
        PageImpl<TransactionResponse> page = new PageImpl<>(List.of(TransactionResponse.builder().type("TOPUP").build()));
        when(walletService.getTransactionHistory(expectedUserId, pageable)).thenReturn(page);

        ResponseEntity<?> result = controller.getTransactions(principal, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(page);
        verify(walletService).getTransactionHistory(expectedUserId, pageable);
    }

    @Test
    void getTransaction_usesResolvedUserId() {
        Principal principal = principal("erin");
        UUID expectedUserId = resolvedUserId("erin");
        UUID transactionId = UUID.randomUUID();
        TransactionResponse response = TransactionResponse.builder().id(transactionId).type("TOPUP").build();
        when(walletService.getTransaction(expectedUserId, transactionId)).thenReturn(response);

        ResponseEntity<TransactionResponse> result = controller.getTransaction(principal, transactionId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).getTransaction(expectedUserId, transactionId);
    }

    @Test
    void reset_usesResolvedUserId() {
        Principal principal = principal("frank");
        UUID expectedUserId = resolvedUserId("frank");
        WalletResponse response = WalletResponse.builder().userId(expectedUserId).build();
        when(walletService.resetWallet(expectedUserId)).thenReturn(response);

        ResponseEntity<WalletResponse> result = controller.reset(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).resetWallet(expectedUserId);
    }

    @Test
    void createHold_overridesUserIdFromPrincipal() {
        Principal principal = principal("grace");
        UUID expectedUserId = resolvedUserId("grace");
        HoldRequest request = HoldRequest.builder()
                .userId(UUID.randomUUID())
                .auctionId(UUID.randomUUID())
                .bidId(UUID.randomUUID())
                .amount(30_000L)
                .build();
        HoldResponse response = HoldResponse.builder().userId(expectedUserId).status("ACTIVE").build();
        when(walletService.createHold(request)).thenReturn(response);

        ResponseEntity<HoldResponse> result = controller.createHold(principal, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request.getUserId()).isEqualTo(expectedUserId);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).createHold(request);
    }

    @Test
    void releaseAndCaptureHold_delegateToService() {
        UUID holdId = UUID.randomUUID();
        HoldResponse released = HoldResponse.builder().holdId(holdId).status("RELEASED").build();
        HoldResponse captured = HoldResponse.builder().holdId(holdId).status("CAPTURED").build();
        when(walletService.releaseHold(holdId)).thenReturn(released);
        when(walletService.captureHold(holdId)).thenReturn(captured);

        ResponseEntity<HoldResponse> releaseResult = controller.releaseHold(holdId);
        ResponseEntity<HoldResponse> captureResult = controller.captureHold(holdId);

        assertThat(releaseResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(releaseResult.getBody()).isSameAs(released);
        assertThat(captureResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(captureResult.getBody()).isSameAs(captured);
        verify(walletService).releaseHold(holdId);
        verify(walletService).captureHold(holdId);
    }

    @Test
    void getWallet_throwsWhenPrincipalIsNull() {
        assertThatThrownBy(() -> controller.getWallet(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User is not authenticated.");
    }

    @Test
    void topUp_throwsWhenPrincipalNameIsNull() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(null);

        assertThatThrownBy(() -> controller.topUp(principal, new TopUpRequest(10_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User is not authenticated.");
    }

    private Principal principal(String subject) {
        return () -> subject;
    }

    private UUID resolvedUserId(String subject) {
        return UUID.nameUUIDFromBytes(("wallet-user-" + subject).getBytes(StandardCharsets.UTF_8));
    }
}
