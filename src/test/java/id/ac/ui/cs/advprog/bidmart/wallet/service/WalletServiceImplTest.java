package id.ac.ui.cs.advprog.bidmart.wallet.service;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.model.*;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceImplTest {

    @Mock WalletRepository walletRepository;
    @Mock BalanceHoldRepository balanceHoldRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

    @InjectMocks WalletServiceImpl service;

    private UUID userId;
    private UUID auctionId;
    private UUID bidId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        auctionId = UUID.randomUUID();
        bidId = UUID.randomUUID();
        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .availableBalance(100_000L)
                .heldBalance(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getWallet ─────────────────────────────────────────────────────────────

    @Test
    void getWallet_returnsExistingWallet() {
        WalletResponse res = service.getWallet(userId);
        assertThat(res.getAvailableBalance()).isEqualTo(100_000L);
        assertThat(res.getUserId()).isEqualTo(userId);
        verify(walletRepository, never()).findByUserIdForUpdate(userId);
    }

    @Test
    void getWallet_createsWalletIfNotExists() {
        UUID newUser = UUID.randomUUID();
        when(walletRepository.findByUserId(newUser)).thenReturn(Optional.empty());

        WalletResponse res = service.getWallet(newUser);

        assertThat(res.getAvailableBalance()).isZero();
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void findOrCreateWallet_ignoresLegacyRecordWhenItIsSameWallet() {
        UUID legacyUserId = legacyUserId(userId);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(legacyUserId)).thenReturn(Optional.of(wallet));

        Wallet result = service.findOrCreateWallet(userId);

        assertThat(result).isSameAs(wallet);
        verify(walletRepository, never()).delete(any());
    }

    @Test
    void findOrCreateWallet_claimsLegacyWalletAndRewritesHoldOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID legacyUserId = legacyUserId(ownerId);
        Wallet legacyWallet = walletWithBalance(legacyUserId, 75_000L, 25_000L);
        BalanceHold legacyHold = BalanceHold.builder()
                .id(UUID.randomUUID())
                .walletId(legacyWallet.getId())
                .userId(legacyUserId)
                .auctionId(auctionId)
                .amount(25_000L)
                .status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findByUserIdForUpdate(ownerId)).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdForUpdate(legacyUserId)).thenReturn(Optional.of(legacyWallet));
        when(balanceHoldRepository.findAllByWalletId(legacyWallet.getId())).thenReturn(List.of(legacyHold));

        Wallet result = service.findOrCreateWallet(ownerId);

        assertThat(result).isSameAs(legacyWallet);
        assertThat(legacyWallet.getUserId()).isEqualTo(ownerId);
        assertThat(legacyHold.getUserId()).isEqualTo(ownerId);
        assertThat(legacyHold.getUpdatedAt()).isNotNull();
        verify(balanceHoldRepository).saveAll(List.of(legacyHold));
        verify(walletRepository).save(legacyWallet);
    }

    @Test
    void findOrCreateWallet_mergesSeparateLegacyWalletAndTransfersRecords() {
        UUID ownerId = UUID.randomUUID();
        UUID legacyUserId = legacyUserId(ownerId);
        Wallet target = walletWithBalance(ownerId, 10_000L, 5_000L);
        Wallet legacy = walletWithBalance(legacyUserId, 15_000L, 7_000L);
        legacy.setFrozen(true);
        BalanceHold legacyHold = BalanceHold.builder()
                .id(UUID.randomUUID())
                .walletId(legacy.getId())
                .userId(legacyUserId)
                .auctionId(auctionId)
                .amount(7_000L)
                .status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        WalletTransaction legacyTransaction = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(legacy.getId())
                .type(TransactionType.HOLD)
                .amount(-7_000L)
                .balanceAfter(15_000L)
                .description("legacy")
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findByUserIdForUpdate(ownerId)).thenReturn(Optional.of(target));
        when(walletRepository.findByUserIdForUpdate(legacyUserId)).thenReturn(Optional.of(legacy));
        when(balanceHoldRepository.findAllByWalletId(legacy.getId())).thenReturn(List.of(legacyHold));
        when(walletTransactionRepository.findAllByWalletId(legacy.getId())).thenReturn(List.of(legacyTransaction));

        Wallet result = service.findOrCreateWallet(ownerId);

        assertThat(result).isSameAs(target);
        assertThat(target.getAvailableBalance()).isEqualTo(25_000L);
        assertThat(target.getHeldBalance()).isEqualTo(12_000L);
        assertThat(target.isFrozen()).isTrue();
        assertThat(legacyHold.getWalletId()).isEqualTo(target.getId());
        assertThat(legacyHold.getUserId()).isEqualTo(ownerId);
        assertThat(legacyTransaction.getWalletId()).isEqualTo(target.getId());
        verify(balanceHoldRepository).saveAll(List.of(legacyHold));
        verify(walletTransactionRepository).saveAll(List.of(legacyTransaction));
        verify(walletRepository).delete(legacy);
    }

    @Test
    void findOrCreateWallet_mergesLegacyWalletWithoutFreezingWhenBothWalletsAreActive() {
        Wallet result = mergeLegacyWalletWithFrozenState(false, false);

        assertThat(result.isFrozen()).isFalse();
    }

    @Test
    void findOrCreateWallet_keepsTargetFrozenWhenMergingLegacyWallet() {
        Wallet result = mergeLegacyWalletWithFrozenState(true, false);

        assertThat(result.isFrozen()).isTrue();
    }

    @Test
    void findOrCreateWallet_throwsWhenLegacyMergeWouldMakeAvailableBalanceNegative() {
        UUID ownerId = UUID.randomUUID();
        UUID legacyUserId = legacyUserId(ownerId);
        Wallet target = walletWithBalance(ownerId, 0L, 0L);
        Wallet legacy = walletWithBalance(legacyUserId, -1L, 0L);

        when(walletRepository.findByUserIdForUpdate(ownerId)).thenReturn(Optional.of(target));
        when(walletRepository.findByUserIdForUpdate(legacyUserId)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.findOrCreateWallet(ownerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo tidak boleh negatif");
    }

    // ── topUp ─────────────────────────────────────────────────────────────────

    @Test
    void topUp_increasesAvailableBalance() {
        WalletResponse res = service.topUp(userId, new TopUpRequest(50_000L));

        assertThat(res.getAvailableBalance()).isEqualTo(150_000L);
        verify(walletTransactionRepository).save(any());
    }

    @Test
    void topUp_throwsIfAmountNotPositive() {
        assertThatThrownBy(() -> service.topUp(userId, new TopUpRequest(0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topUp_throwsWhenWalletFrozen() {
        wallet.setFrozen(true);

        assertThatThrownBy(() -> service.topUp(userId, new TopUpRequest(50_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    // ── withdraw ──────────────────────────────────────────────────────────────

    @Test
    void withdraw_decreasesBalance() {
        WithdrawRequest req = WithdrawRequest.builder()
                .amount(10_000L).bankCode("BCA").accountNumber("123").accountName("Test").build();

        WithdrawResponse res = service.withdraw(userId, req);

        assertThat(res.getAmount()).isEqualTo(10_000L);
        assertThat(res.getFee()).isEqualTo(5_000L);
        assertThat(wallet.getAvailableBalance()).isEqualTo(85_000L);
    }

    @Test
    void withdraw_throwsIfInsufficientBalance() {
        WithdrawRequest req = WithdrawRequest.builder()
                .amount(200_000L).bankCode("BCA").accountNumber("123").accountName("Test").build();

        assertThatThrownBy(() -> service.withdraw(userId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo tidak mencukupi");
    }

    @Test
    void withdraw_throwsWhenWalletFrozen() {
        wallet.setFrozen(true);
        WithdrawRequest req = WithdrawRequest.builder()
                .amount(10_000L).bankCode("BCA").accountNumber("123").accountName("Test").build();

        assertThatThrownBy(() -> service.withdraw(userId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    // ── transaction history/detail ────────────────────────────────────────────

    @Test
    void getTransactionHistory_returnsMappedPageForCurrentUser() {
        WalletTransaction txn = WalletTransaction.builder()
                .id(UUID.randomUUID()).walletId(wallet.getId())
                .type(TransactionType.TOPUP).amount(50_000L).balanceAfter(150_000L)
                .description("Top-up saldo").referenceId(auctionId).createdAt(LocalDateTime.now()).build();

        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(txn)));

        Page<TransactionResponse> page = service.getTransactionHistory(userId, Pageable.unpaged());

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> {
                    assertThat(response.getId()).isEqualTo(txn.getId());
                    assertThat(response.getType()).isEqualTo("TOPUP");
                    assertThat(response.getReferenceId()).isEqualTo(auctionId);
                });
    }

    @Test
    void getTransactionHistory_returnsEmptyPageWhenWalletDoesNotExist() {
        UUID newUser = UUID.randomUUID();
        when(walletRepository.findByUserId(newUser)).thenReturn(Optional.empty());

        Page<TransactionResponse> page = service.getTransactionHistory(newUser, Pageable.unpaged());

        assertThat(page).isEmpty();
        verify(walletTransactionRepository, never()).findByWalletIdOrderByCreatedAtDesc(any(), any());
        verify(walletRepository, never()).findByUserIdForUpdate(newUser);
    }

    @Test
    void getTransaction_returnsOwnedTransaction() {
        UUID transactionId = UUID.randomUUID();
        WalletTransaction txn = WalletTransaction.builder()
                .id(transactionId).walletId(wallet.getId())
                .type(TransactionType.WITHDRAW).amount(-15_000L).balanceAfter(85_000L)
                .description("withdraw").createdAt(LocalDateTime.now()).build();
        when(walletTransactionRepository.findById(transactionId)).thenReturn(Optional.of(txn));

        TransactionResponse response = service.getTransaction(userId, transactionId);

        assertThat(response.getId()).isEqualTo(transactionId);
        assertThat(response.getType()).isEqualTo("WITHDRAW");
        assertThat(response.getBalanceAfter()).isEqualTo(85_000L);
    }

    @Test
    void getTransaction_throwsIfTransactionDoesNotExist() {
        UUID transactionId = UUID.randomUUID();
        when(walletTransactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(userId, transactionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void getTransaction_throwsIfTransactionBelongsToAnotherWallet() {
        UUID transactionId = UUID.randomUUID();
        WalletTransaction txn = WalletTransaction.builder()
                .id(transactionId).walletId(UUID.randomUUID())
                .type(TransactionType.TOPUP).amount(10_000L).balanceAfter(10_000L)
                .description("other wallet").createdAt(LocalDateTime.now()).build();
        when(walletTransactionRepository.findById(transactionId)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> service.getTransaction(userId, transactionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void resetWallet_releasesActiveHoldsAndIgnoresInactiveHolds() {
        wallet.setAvailableBalance(40_000L);
        wallet.setHeldBalance(60_000L);
        BalanceHold active = activeHold(UUID.randomUUID(), 60_000L);
        BalanceHold released = activeHold(UUID.randomUUID(), 10_000L);
        released.setStatus(HoldStatus.RELEASED);
        when(balanceHoldRepository.findAllByWalletId(wallet.getId())).thenReturn(List.of(active, released));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletResponse response = service.resetWallet(userId);

        assertThat(active.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(released.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(response.getAvailableBalance()).isZero();
        assertThat(response.getHeldBalance()).isZero();
        assertThat(wallet.getAvailableBalance()).isZero();
        assertThat(wallet.getHeldBalance()).isZero();
    }

    // ── createHold ────────────────────────────────────────────────────────────

    @Test
    void createHold_movesFromAvailableToHeld() {
        when(balanceHoldRepository.findByUserIdAndAuctionIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(auctionId).bidId(bidId).amount(30_000L).build();
        HoldResponse res = service.createHold(req);

        assertThat(res.getStatus()).isEqualTo("ACTIVE");
        assertThat(res.getAmount()).isEqualTo(30_000L);
        assertThat(wallet.getAvailableBalance()).isEqualTo(70_000L);
        assertThat(wallet.getHeldBalance()).isEqualTo(30_000L);
    }

    @Test
    void createHold_throwsIfInsufficientBalance() {
        when(balanceHoldRepository.findByUserIdAndAuctionIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());

        HoldRequest req = HoldRequest.builder()
                .userId(userId).auctionId(auctionId).bidId(bidId).amount(200_000L).build();

        assertThatThrownBy(() -> service.createHold(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo tidak mencukupi");
    }

    @Test
    void createHold_replacesExistingActiveHold() {
        wallet.setAvailableBalance(80_000L);
        wallet.setHeldBalance(20_000L);

        BalanceHold existing = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(wallet.getId()).userId(userId)
                .auctionId(auctionId).amount(20_000L).status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByUserIdAndAuctionIdAndStatus(userId, auctionId, HoldStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createHold(HoldRequest.builder()
                .userId(userId).auctionId(auctionId).bidId(bidId).amount(30_000L).build());

        assertThat(existing.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(wallet.getHeldBalance()).isEqualTo(30_000L);
    }

    @Test
    void createHold_throwsWhenReplacingHoldWouldExceedAvailableAfterRelease() {
        wallet.setAvailableBalance(5_000L);
        wallet.setHeldBalance(20_000L);
        BalanceHold existing = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(wallet.getId()).userId(userId)
                .auctionId(auctionId).amount(20_000L).status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByUserIdAndAuctionIdAndStatus(userId, auctionId, HoldStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createHold(HoldRequest.builder()
                .userId(userId).auctionId(auctionId).bidId(bidId).amount(30_000L).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo tidak mencukupi");
    }

    @Test
    void createHold_throwsIfAmountNotPositive() {
        assertThatThrownBy(() -> service.createHold(HoldRequest.builder()
                .userId(userId).auctionId(auctionId).bidId(bidId).amount(0L).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── releaseHold ───────────────────────────────────────────────────────────

    @Test
    void releaseHold_releasesActiveHold() {
        wallet.setAvailableBalance(70_000L);
        wallet.setHeldBalance(30_000L);
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HoldResponse res = service.releaseHold(holdId);

        assertThat(res.getStatus()).isEqualTo("RELEASED");
        assertThat(wallet.getAvailableBalance()).isEqualTo(100_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    void releaseHold_idempotent_whenAlreadyReleased() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        hold.setStatus(HoldStatus.RELEASED);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

        HoldResponse res = service.releaseHold(holdId);

        assertThat(res.getStatus()).isEqualTo("RELEASED");
        verify(walletRepository, never()).findById(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void releaseHold_throwsIfAlreadyCaptured() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        hold.setStatus(HoldStatus.CAPTURED);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.releaseHold(holdId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capture");
    }

    @Test
    void releaseHold_throwsIfHoldDoesNotExist() {
        UUID holdId = UUID.randomUUID();
        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseHold(holdId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hold not found");
    }

    @Test
    void releaseHold_throwsIfWalletDoesNotExist() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseHold(holdId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Wallet not found");
    }

    // ── captureHold ───────────────────────────────────────────────────────────

    @Test
    void captureHold_capturesActiveHold() {
        wallet.setAvailableBalance(70_000L);
        wallet.setHeldBalance(30_000L);
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HoldResponse res = service.captureHold(holdId);

        assertThat(res.getStatus()).isEqualTo("CAPTURED");
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    void captureHold_idempotent_whenAlreadyCaptured() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        hold.setStatus(HoldStatus.CAPTURED);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

        HoldResponse res = service.captureHold(holdId);

        assertThat(res.getStatus()).isEqualTo("CAPTURED");
        verify(walletRepository, never()).findById(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void captureHold_throwsIfAlreadyReleased() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        hold.setStatus(HoldStatus.RELEASED);

        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.captureHold(holdId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("release");
    }

    @Test
    void captureHold_throwsIfHoldDoesNotExist() {
        UUID holdId = UUID.randomUUID();
        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.captureHold(holdId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hold not found");
    }

    @Test
    void captureHold_throwsIfWalletDoesNotExist() {
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.captureHold(holdId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Wallet not found");
    }

    @Test
    void captureHold_throwsIfHeldBalanceWouldBecomeNegative() {
        wallet.setAvailableBalance(90_000L);
        wallet.setHeldBalance(10_000L);
        UUID holdId = UUID.randomUUID();
        BalanceHold hold = activeHold(holdId, 30_000L);
        when(balanceHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.captureHold(holdId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo tidak boleh negatif");
    }

    // ── settleAuction ─────────────────────────────────────────────────────────

    @Test
    void settleAuction_capturesWinnersAndReleasesOthers() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();

        Wallet winnerWallet = walletWithBalance(winnerId, 0L, 50_000L);
        Wallet loserWallet  = walletWithBalance(loserId,  0L, 30_000L);

        BalanceHold winnerHold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(winnerWallet.getId())
                .userId(winnerId).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        BalanceHold loserHold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(loserWallet.getId())
                .userId(loserId).auctionId(auctionId).amount(30_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(winnerHold, loserHold));
        when(walletRepository.findById(winnerWallet.getId())).thenReturn(Optional.of(winnerWallet));
        when(walletRepository.findById(loserWallet.getId())).thenReturn(Optional.of(loserWallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuctionSettleResponse res = service.settleAuction(auctionId, null,
                List.of(new AuctionSettleRequest.WinnerEntry(winnerId, 50_000L)));

        assertThat(res.getCaptured()).hasSize(1);
        assertThat(res.getReleased()).hasSize(1);
        assertThat(winnerHold.getStatus()).isEqualTo(HoldStatus.CAPTURED);
        assertThat(loserHold.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(winnerWallet.getHeldBalance()).isZero();
        assertThat(loserWallet.getAvailableBalance()).isEqualTo(30_000L);
    }

    @Test
    void settleAuction_noActiveHolds_returnsEmpty() {
        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        AuctionSettleResponse res = service.settleAuction(auctionId, null,
                List.of(new AuctionSettleRequest.WinnerEntry(userId, 50_000L)));

        assertThat(res.getCaptured()).isEmpty();
        assertThat(res.getReleased()).isEmpty();
    }

    @Test
    void settleAuction_refundsUnusedWinnerHoldAmount() {
        UUID winnerId = UUID.randomUUID();
        Wallet winnerWallet = walletWithBalance(winnerId, 0L, 50_000L);
        BalanceHold winnerHold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(winnerWallet.getId())
                .userId(winnerId).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(winnerHold));
        when(walletRepository.findById(winnerWallet.getId())).thenReturn(Optional.of(winnerWallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuctionSettleResponse res = service.settleAuction(auctionId, null,
                List.of(new AuctionSettleRequest.WinnerEntry(winnerId, 40_000L)));

        assertThat(res.getCaptured()).singleElement().extracting(AuctionSettleResponse.CapturedEntry::getAmount)
                .isEqualTo(40_000L);
        assertThat(winnerWallet.getAvailableBalance()).isEqualTo(10_000L);
        assertThat(winnerWallet.getHeldBalance()).isZero();
        verify(walletTransactionRepository, atLeast(2)).save(any());
    }

    @Test
    void settleAuction_throwsWhenCaptureAmountExceedsHold() {
        UUID winnerId = UUID.randomUUID();
        Wallet winnerWallet = walletWithBalance(winnerId, 0L, 50_000L);
        BalanceHold winnerHold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(winnerWallet.getId())
                .userId(winnerId).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(winnerHold));
        when(walletRepository.findById(winnerWallet.getId())).thenReturn(Optional.of(winnerWallet));

        assertThatThrownBy(() -> service.settleAuction(auctionId, null,
                List.of(new AuctionSettleRequest.WinnerEntry(winnerId, 60_000L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds hold");
    }

    @Test
    void settleAuction_releasesAllHoldsWhenWinnersAreNull() {
        UUID bidderId = UUID.randomUUID();
        Wallet bidderWallet = walletWithBalance(bidderId, 0L, 25_000L);
        BalanceHold hold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(bidderWallet.getId())
                .userId(bidderId).auctionId(auctionId).amount(25_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold));
        when(walletRepository.findById(bidderWallet.getId())).thenReturn(Optional.of(bidderWallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuctionSettleResponse response = service.settleAuction(auctionId, null, null);

        assertThat(response.getCaptured()).isEmpty();
        assertThat(response.getReleased()).singleElement()
                .extracting(AuctionSettleResponse.ReleasedEntry::getAmount)
                .isEqualTo(25_000L);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    }

    @Test
    void settleAuction_throwsWhenWinnerCaptureAmountIsNotPositive() {
        UUID winnerId = UUID.randomUUID();
        Wallet winnerWallet = walletWithBalance(winnerId, 0L, 50_000L);
        BalanceHold hold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(winnerWallet.getId())
                .userId(winnerId).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold));
        when(walletRepository.findById(winnerWallet.getId())).thenReturn(Optional.of(winnerWallet));

        assertThatThrownBy(() -> service.settleAuction(auctionId, null,
                List.of(new AuctionSettleRequest.WinnerEntry(winnerId, 0L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capture amount must be positive");
    }

    @Test
    void settleAuction_throwsWhenHoldWalletDoesNotExist() {
        BalanceHold hold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(UUID.randomUUID())
                .userId(UUID.randomUUID()).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold));
        when(walletRepository.findById(hold.getWalletId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settleAuction(auctionId, null, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wallet not found for hold");
    }

    @Test
    void settleAuction_paysNewSellerWalletWhenSellerIsProvided() {
        UUID winnerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Wallet winnerWallet = walletWithBalance(winnerId, 0L, 50_000L);
        BalanceHold hold = BalanceHold.builder()
                .id(UUID.randomUUID()).walletId(winnerWallet.getId())
                .userId(winnerId).auctionId(auctionId).amount(50_000L)
                .status(HoldStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold));
        when(walletRepository.findById(winnerWallet.getId())).thenReturn(Optional.of(winnerWallet));
        when(walletRepository.findByUserId(sellerId)).thenReturn(Optional.empty());
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuctionSettleResponse response = service.settleAuction(auctionId, sellerId,
                List.of(new AuctionSettleRequest.WinnerEntry(winnerId, 50_000L)));

        assertThat(response.getCaptured()).hasSize(1);
        verify(walletRepository).findByUserId(sellerId);
        verify(walletTransactionRepository, atLeastOnce()).save(argThat(txn ->
                txn.getType() == TransactionType.PAYMENT_RECEIVED && txn.getAmount() == 50_000L));
    }

    // ── releaseAllHoldsForAuction ─────────────────────────────────────────────

    @Test
    void releaseAllHolds_releasesAllActiveHolds() {
        wallet.setAvailableBalance(0L);
        wallet.setHeldBalance(60_000L);

        BalanceHold hold1 = activeHold(UUID.randomUUID(), 30_000L);
        BalanceHold hold2 = activeHold(UUID.randomUUID(), 30_000L);

        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold1, hold2));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(balanceHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuctionReleaseAllResponse res = service.releaseAllHoldsForAuction(auctionId);

        assertThat(res.getReleased()).hasSize(2);
        assertThat(hold1.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(hold2.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(wallet.getAvailableBalance()).isEqualTo(60_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    void releaseAllHolds_throwsWhenWalletDoesNotExist() {
        BalanceHold hold = activeHold(UUID.randomUUID(), 30_000L);
        when(balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE))
                .thenReturn(List.of(hold));
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseAllHoldsForAuction(auctionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wallet not found for hold");
    }

    // ── freeze ───────────────────────────────────────────────────────────────

    @Test
    void freezeWallet_marksWalletFrozenAndWritesAuditRecord() {
        WalletResponse res = service.freezeWallet(userId, "suspended");

        assertThat(res.isFrozen()).isTrue();
        assertThat(wallet.isFrozen()).isTrue();
        verify(walletTransactionRepository).save(argThat(txn ->
                txn.getType() == TransactionType.WALLET_FROZEN && txn.getAmount() == 0L));
    }

    @Test
    void freezeWallet_withNullReasonWritesGenericAuditRecord() {
        service.freezeWallet(userId, null);

        verify(walletTransactionRepository).save(argThat(txn ->
                txn.getType() == TransactionType.WALLET_FROZEN
                        && txn.getDescription().equals("Wallet frozen")));
    }

    @Test
    void freezeWallet_withBlankReasonWritesGenericAuditRecord() {
        service.freezeWallet(userId, "   ");

        verify(walletTransactionRepository).save(argThat(txn ->
                txn.getType() == TransactionType.WALLET_FROZEN
                        && txn.getDescription().equals("Wallet frozen")));
    }

    @Test
    void freezeWallet_doesNotWriteAuditRecordWhenAlreadyFrozen() {
        wallet.setFrozen(true);

        WalletResponse response = service.freezeWallet(userId, "ignored");

        assertThat(response.isFrozen()).isTrue();
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    // ── admin methods ─────────────────────────────────────────────────────────

    @Test
    void getWalletByUserIdForAdmin_returnsWallet() {
        WalletResponse res = service.getWalletByUserIdForAdmin(userId);
        assertThat(res.getUserId()).isEqualTo(userId);
    }

    @Test
    void getWalletByUserIdForAdmin_throwsIfNotFound() {
        UUID unknown = UUID.randomUUID();
        when(walletRepository.findByUserId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWalletByUserIdForAdmin(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTransactionHistoryForAdmin_returnsPage() {
        WalletTransaction txn = WalletTransaction.builder()
                .id(UUID.randomUUID()).walletId(wallet.getId())
                .type(TransactionType.TOPUP).amount(50_000L).balanceAfter(150_000L)
                .description("Test").createdAt(LocalDateTime.now()).build();

        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(
                eq(wallet.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(txn)));

        Page<TransactionResponse> page =
                service.getTransactionHistoryForAdmin(userId, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getType()).isEqualTo("TOPUP");
    }

    @Test
    void getTransactionHistoryForAdmin_throwsIfNotFound() {
        UUID unknown = UUID.randomUUID();
        when(walletRepository.findByUserId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getTransactionHistoryForAdmin(unknown, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BalanceHold activeHold(UUID holdId, long amount) {
        return BalanceHold.builder()
                .id(holdId).walletId(wallet.getId()).userId(userId)
                .auctionId(auctionId).amount(amount).status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();
    }

    private Wallet walletWithBalance(UUID ownerId, long available, long held) {
        return Wallet.builder()
                .id(UUID.randomUUID()).userId(ownerId)
                .availableBalance(available).heldBalance(held)
                .createdAt(LocalDateTime.now()).build();
    }

    private UUID legacyUserId(UUID ownerId) {
        return UUID.nameUUIDFromBytes(("wallet-user-" + ownerId).getBytes(StandardCharsets.UTF_8));
    }

    private Wallet mergeLegacyWalletWithFrozenState(boolean targetFrozen, boolean legacyFrozen) {
        UUID ownerId = UUID.randomUUID();
        UUID legacyUserId = legacyUserId(ownerId);
        Wallet target = walletWithBalance(ownerId, 10_000L, 0L);
        target.setFrozen(targetFrozen);
        Wallet legacy = walletWithBalance(legacyUserId, 5_000L, 0L);
        legacy.setFrozen(legacyFrozen);

        when(walletRepository.findByUserIdForUpdate(ownerId)).thenReturn(Optional.of(target));
        when(walletRepository.findByUserIdForUpdate(legacyUserId)).thenReturn(Optional.of(legacy));
        when(balanceHoldRepository.findAllByWalletId(legacy.getId())).thenReturn(List.of());
        when(walletTransactionRepository.findAllByWalletId(legacy.getId())).thenReturn(List.of());

        return service.findOrCreateWallet(ownerId);
    }
}
