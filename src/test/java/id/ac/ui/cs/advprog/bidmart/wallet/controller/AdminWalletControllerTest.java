package id.ac.ui.cs.advprog.bidmart.wallet.controller;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.TransactionResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.dto.WalletResponse;
import id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminWalletControllerTest {

    private WalletService walletService;
    private AdminWalletController controller;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        controller = new AdminWalletController(walletService);
    }

    @Test
    void getWallet_allowsRequestWhenServiceTokenIsNull() {
        UUID userId = UUID.randomUUID();
        WalletResponse response = WalletResponse.builder().userId(userId).build();
        ReflectionTestUtils.setField(controller, "serviceToken", null);
        when(walletService.getWalletByUserIdForAdmin(userId)).thenReturn(response);

        ResponseEntity<WalletResponse> result = controller.getWallet(userId, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(walletService).getWalletByUserIdForAdmin(userId);
    }

    @Test
    void getTransactions_allowsRequestWhenServiceTokenIsBlank() {
        UUID userId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        PageImpl<TransactionResponse> page = new PageImpl<>(List.of(TransactionResponse.builder().type("TOPUP").build()));
        ReflectionTestUtils.setField(controller, "serviceToken", "   ");
        when(walletService.getTransactionHistoryForAdmin(userId, pageable)).thenReturn(page);

        ResponseEntity<?> result = controller.getTransactions(userId, null, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(page);
        verify(walletService).getTransactionHistoryForAdmin(userId, pageable);
    }
}
