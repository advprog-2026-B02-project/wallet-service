package id.ac.ui.cs.advprog.bidmart.wallet.service;

import id.ac.ui.cs.advprog.bidmart.wallet.dto.*;
import id.ac.ui.cs.advprog.bidmart.wallet.model.*;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WalletServiceImpl implements WalletService {

    private static final long WITHDRAW_FEE = 5000L;
    private static final String LEGACY_WALLET_USER_PREFIX = "wallet-user-";

    private final WalletRepository walletRepository;
    private final BalanceHoldRepository balanceHoldRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletServiceImpl(WalletRepository walletRepository,
                             BalanceHoldRepository balanceHoldRepository,
                             WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.balanceHoldRepository = balanceHoldRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Override
    @Transactional
    public WalletResponse getWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(this::toWalletResponse)
                .orElseGet(() -> toWalletResponse(findOrCreateWallet(userId)));
    }

    @Override
    @Transactional
    public WalletResponse topUp(UUID userId, TopUpRequest request) {
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        Wallet wallet = findOrCreateWallet(userId);
        assertWalletCanMoveFunds(wallet);
        applyBalanceChange(wallet, request.getAmount(), 0);
        walletRepository.save(wallet);
        saveTransaction(wallet, TransactionType.TOPUP, "Top-up saldo", request.getAmount(), null);
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    public WithdrawResponse withdraw(UUID userId, WithdrawRequest request) {
        Wallet wallet = findOrCreateWallet(userId);
        assertWalletCanMoveFunds(wallet);
        long total = request.getAmount() + WITHDRAW_FEE;
        if (wallet.getAvailableBalance() < total) {
            throw new IllegalStateException(
                    String.format("Saldo tidak mencukupi. Tersedia: %d, Dibutuhkan: %d (termasuk biaya %d)",
                            wallet.getAvailableBalance(), total, WITHDRAW_FEE));
        }
        applyBalanceChange(wallet, -total, 0);
        walletRepository.save(wallet);
        WalletTransaction txn = saveTransaction(wallet, TransactionType.WITHDRAW,
                String.format("Penarikan ke %s %s (%s)",
                        request.getBankCode(), request.getAccountNumber(), request.getAccountName()),
                -total, null);
        return WithdrawResponse.builder()
                .transactionId(txn.getId())
                .amount(request.getAmount())
                .fee(WITHDRAW_FEE)
                .netAmount(request.getAmount() - WITHDRAW_FEE)
                .status("PROCESSING")
                .estimatedCompletion(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(UUID userId, Pageable pageable) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> walletTransactionRepository
                        .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                        .map(this::toTransactionResponse))
                .orElseGet(() -> Page.empty(pageable));
    }

    @Override
    @Transactional
    public TransactionResponse getTransaction(UUID userId, UUID transactionId) {
        Wallet wallet = findOrCreateWallet(userId);
        WalletTransaction txn = walletTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        if (!txn.getWalletId().equals(wallet.getId())) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }
        return toTransactionResponse(txn);
    }

    @Override
    @Transactional
    public WalletResponse resetWallet(UUID userId) {
        Wallet wallet = findOrCreateWallet(userId);
        balanceHoldRepository.findAllByWalletId(wallet.getId()).stream()
                .filter(h -> h.getStatus() == HoldStatus.ACTIVE)
                .forEach(h -> releaseHoldInternal(wallet, h));
        wallet.setAvailableBalance(0);
        wallet.setHeldBalance(0);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    public HoldResponse createHold(HoldRequest request) {
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("Hold amount must be positive");
        }
        Wallet wallet = findOrCreateWallet(request.getUserId());
        Optional<BalanceHold> existingHold = balanceHoldRepository.findByUserIdAndAuctionIdAndStatus(
                request.getUserId(), request.getAuctionId(), HoldStatus.ACTIVE);

        if (existingHold.isPresent()) {
            return replaceExistingHold(wallet, existingHold.get(), request);
        }

        if (wallet.getAvailableBalance() < request.getAmount()) {
            throw new IllegalStateException(
                    String.format("Saldo tidak mencukupi. Tersedia: %d, Dibutuhkan: %d",
                            wallet.getAvailableBalance(), request.getAmount()));
        }
        applyBalanceChange(wallet, -request.getAmount(), request.getAmount());
        walletRepository.save(wallet);

        BalanceHold hold = BalanceHold.builder()
                .walletId(wallet.getId())
                .userId(request.getUserId())
                .auctionId(request.getAuctionId())
                .bidId(request.getBidId())
                .amount(request.getAmount())
                .status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        balanceHoldRepository.save(hold);
        saveTransaction(wallet, TransactionType.HOLD, "Hold balance for bid", -request.getAmount(), request.getAuctionId());
        return toHoldResponse(hold);
    }

    private HoldResponse replaceExistingHold(Wallet wallet, BalanceHold hold, HoldRequest request) {
        long availableAfterRelease = wallet.getAvailableBalance() + hold.getAmount();
        if (availableAfterRelease < request.getAmount()) {
            throw new IllegalStateException(
                    String.format("Saldo tidak mencukupi. Tersedia: %d, Dibutuhkan: %d",
                            availableAfterRelease, request.getAmount()));
        }

        releaseHoldInternal(wallet, hold);

        BalanceHold replacement = BalanceHold.builder()
                .walletId(wallet.getId())
                .userId(request.getUserId())
                .auctionId(request.getAuctionId())
                .bidId(request.getBidId())
                .amount(request.getAmount())
                .status(HoldStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        applyBalanceChange(wallet, -request.getAmount(), request.getAmount());
        walletRepository.save(wallet);
        BalanceHold savedReplacement = balanceHoldRepository.save(replacement);
        saveTransaction(wallet, TransactionType.HOLD, "Hold balance for bid", -request.getAmount(), request.getAuctionId());
        return toHoldResponse(savedReplacement);
    }

    @Override
    @Transactional
    public HoldResponse releaseHold(UUID holdId) {
        BalanceHold hold = balanceHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return toHoldResponse(hold);
        }
        if (hold.getStatus() == HoldStatus.CAPTURED) {
            throw new IllegalStateException("Hold sudah di-capture, tidak bisa di-release: " + holdId);
        }
        Wallet wallet = walletRepository.findById(hold.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found"));
        releaseHoldInternal(wallet, hold);
        walletRepository.save(wallet);
        return toHoldResponse(hold);
    }

    @Override
    @Transactional
    public HoldResponse captureHold(UUID holdId) {
        BalanceHold hold = balanceHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));
        if (hold.getStatus() == HoldStatus.CAPTURED) {
            return toHoldResponse(hold);
        }
        if (hold.getStatus() == HoldStatus.RELEASED) {
            throw new IllegalStateException("Hold sudah di-release, tidak bisa di-capture: " + holdId);
        }
        Wallet wallet = walletRepository.findById(hold.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found"));
        applyBalanceChange(wallet, 0, -hold.getAmount());
        walletRepository.save(wallet);
        hold.setStatus(HoldStatus.CAPTURED);
        hold.setUpdatedAt(LocalDateTime.now());
        balanceHoldRepository.save(hold);
        saveTransaction(wallet, TransactionType.CAPTURE, "Bid payment", -hold.getAmount(), hold.getAuctionId());
        return toHoldResponse(hold);
    }

    @Override
    @Transactional
    public AuctionSettleResponse settleAuction(UUID auctionId, UUID sellerId, List<AuctionSettleRequest.WinnerEntry> winners) {
        List<BalanceHold> activeHolds = balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE);

        List<AuctionSettleRequest.WinnerEntry> safeWinners =
                winners == null ? List.of() : winners;

        Set<UUID> winnerIds = safeWinners.stream()
                .map(AuctionSettleRequest.WinnerEntry::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, Long> winnerAmounts = safeWinners.stream()
                .collect(Collectors.toMap(
                        AuctionSettleRequest.WinnerEntry::getUserId,
                        AuctionSettleRequest.WinnerEntry::getCaptureAmount));

        List<AuctionSettleResponse.CapturedEntry> captured = new ArrayList<>();
        List<AuctionSettleResponse.ReleasedEntry> released = new ArrayList<>();

        for (BalanceHold hold : activeHolds) {
            Wallet wallet = walletRepository.findById(hold.getWalletId())
                    .orElseThrow(() -> new IllegalStateException("Wallet not found for hold: " + hold.getId()));

            if (winnerIds.contains(hold.getUserId())) {
                long captureAmount = winnerAmounts.getOrDefault(hold.getUserId(), hold.getAmount());
                validateCaptureAmount(hold, captureAmount);
                long refundAmount = hold.getAmount() - captureAmount;

                applyBalanceChange(wallet, refundAmount, -hold.getAmount());
                walletRepository.save(wallet);

                hold.setStatus(HoldStatus.CAPTURED);
                hold.setUpdatedAt(LocalDateTime.now());
                balanceHoldRepository.save(hold);

                WalletTransaction txn = saveTransaction(wallet, TransactionType.CAPTURE,
                        "Pembayaran lelang - pemenang", -captureAmount, auctionId);
                if (refundAmount > 0) {
                    saveTransaction(wallet, TransactionType.RELEASE,
                            "Refund sisa hold setelah settlement", refundAmount, auctionId);
                }

                if (sellerId != null) {
                    Wallet sellerWallet = walletRepository.findByUserId(sellerId)
                            .orElseGet(() -> {
                                Wallet w = new Wallet();
                                w.setUserId(sellerId);
                                w.setAvailableBalance(0L);
                                w.setHeldBalance(0L);
                                w.setCreatedAt(LocalDateTime.now());
                                w.setUpdatedAt(LocalDateTime.now());
                                return walletRepository.save(w);
                            });
                    applyBalanceChange(sellerWallet, captureAmount, 0L);
                    walletRepository.save(sellerWallet);
                    saveTransaction(sellerWallet, TransactionType.PAYMENT_RECEIVED,
                            "Penerimaan dana lelang", captureAmount, auctionId);
                }

                captured.add(new AuctionSettleResponse.CapturedEntry(hold.getId(), hold.getUserId(), captureAmount, txn.getId()));
            } else {
                releaseHoldInternal(wallet, hold);
                walletRepository.save(wallet);
                released.add(new AuctionSettleResponse.ReleasedEntry(hold.getId(), hold.getUserId(), hold.getAmount()));
            }
        }

        return AuctionSettleResponse.builder()
                .auctionId(auctionId)
                .captured(captured)
                .released(released)
                .settledAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public AuctionReleaseAllResponse releaseAllHoldsForAuction(UUID auctionId) {
        List<BalanceHold> activeHolds = balanceHoldRepository.findByAuctionIdAndStatus(auctionId, HoldStatus.ACTIVE);
        List<AuctionSettleResponse.ReleasedEntry> released = new ArrayList<>();
        for (BalanceHold hold : activeHolds) {
            Wallet wallet = walletRepository.findById(hold.getWalletId())
                    .orElseThrow(() -> new IllegalStateException("Wallet not found for hold: " + hold.getId()));
            releaseHoldInternal(wallet, hold);
            walletRepository.save(wallet);
            released.add(new AuctionSettleResponse.ReleasedEntry(hold.getId(), hold.getUserId(), hold.getAmount()));
        }
        return AuctionReleaseAllResponse.builder()
                .auctionId(auctionId)
                .released(released)
                .releasedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public WalletResponse freezeWallet(UUID userId, String reason) {
        Wallet wallet = findOrCreateWallet(userId);
        if (!wallet.isFrozen()) {
            wallet.setFrozen(true);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);
            saveTransaction(wallet, TransactionType.WALLET_FROZEN,
                    "Wallet frozen" + (reason == null || reason.isBlank() ? "" : ": " + reason),
                    0, null);
        }
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserIdForAdmin(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistoryForAdmin(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));
        return walletTransactionRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(this::toTransactionResponse);
    }

    @Transactional
    public Wallet findOrCreateWallet(UUID userId) {
        UUID legacyUserId = legacyWalletUserId(userId);
        Optional<Wallet> existingWallet = walletRepository.findByUserIdForUpdate(userId);
        Optional<Wallet> legacyWallet = walletRepository.findByUserIdForUpdate(legacyUserId);

        if (existingWallet.isPresent()) {
            return legacyWallet
                    .filter(legacy -> !legacy.getId().equals(existingWallet.get().getId()))
                    .map(legacy -> mergeLegacyWallet(existingWallet.get(), legacy, userId))
                    .orElse(existingWallet.get());
        }

        if (legacyWallet.isPresent()) {
            return claimLegacyWallet(legacyWallet.get(), userId);
        }

        return walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            Wallet w = Wallet.builder()
                    .userId(userId)
                    .availableBalance(0)
                    .heldBalance(0)
                    .frozen(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            return walletRepository.save(w);
        });
    }

    private Wallet claimLegacyWallet(Wallet wallet, UUID userId) {
        wallet.setUserId(userId);
        wallet.setUpdatedAt(LocalDateTime.now());
        rewriteHoldOwnership(wallet.getId(), userId);
        return walletRepository.save(wallet);
    }

    private Wallet mergeLegacyWallet(Wallet target, Wallet legacy, UUID userId) {
        applyBalanceChange(target, legacy.getAvailableBalance(), legacy.getHeldBalance());
        target.setFrozen(target.isFrozen() || legacy.isFrozen());
        Wallet savedTarget = walletRepository.save(target);

        List<BalanceHold> legacyHolds = balanceHoldRepository.findAllByWalletId(legacy.getId());
        legacyHolds.forEach(hold -> {
            hold.setWalletId(savedTarget.getId());
            hold.setUserId(userId);
            hold.setUpdatedAt(LocalDateTime.now());
        });
        balanceHoldRepository.saveAll(legacyHolds);

        List<WalletTransaction> legacyTransactions = walletTransactionRepository.findAllByWalletId(legacy.getId());
        legacyTransactions.forEach(transaction -> transaction.setWalletId(savedTarget.getId()));
        walletTransactionRepository.saveAll(legacyTransactions);

        walletRepository.delete(legacy);
        return savedTarget;
    }

    private void rewriteHoldOwnership(UUID walletId, UUID userId) {
        List<BalanceHold> holds = balanceHoldRepository.findAllByWalletId(walletId);
        holds.forEach(hold -> {
            hold.setUserId(userId);
            hold.setUpdatedAt(LocalDateTime.now());
        });
        balanceHoldRepository.saveAll(holds);
    }

    private UUID legacyWalletUserId(UUID userId) {
        String source = LEGACY_WALLET_USER_PREFIX + userId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private void releaseHoldInternal(Wallet wallet, BalanceHold hold) {
        applyBalanceChange(wallet, hold.getAmount(), -hold.getAmount());
        hold.setStatus(HoldStatus.RELEASED);
        hold.setUpdatedAt(LocalDateTime.now());
        balanceHoldRepository.save(hold);
        saveTransaction(wallet, TransactionType.RELEASE, "Release hold, lost bid", hold.getAmount(), hold.getAuctionId());
    }

    private void applyBalanceChange(Wallet wallet, long deltaAvailable, long deltaHeld) {
        long newAvailable = wallet.getAvailableBalance() + deltaAvailable;
        long newHeld = wallet.getHeldBalance() + deltaHeld;
        if (newAvailable < 0 || newHeld < 0) {
            throw new IllegalStateException(
                    String.format("Saldo tidak boleh negatif. available=%d, held=%d", newAvailable, newHeld));
        }
        wallet.setAvailableBalance(newAvailable);
        wallet.setHeldBalance(newHeld);
        wallet.setUpdatedAt(LocalDateTime.now());
    }

    private void assertWalletCanMoveFunds(Wallet wallet) {
        if (wallet.isFrozen()) {
            throw new IllegalStateException("Wallet is frozen for this user");
        }
    }

    private void validateCaptureAmount(BalanceHold hold, long captureAmount) {
        if (captureAmount <= 0) {
            throw new IllegalArgumentException("Capture amount must be positive");
        }
        if (captureAmount > hold.getAmount()) {
            throw new IllegalStateException(
                    String.format("Capture amount %d exceeds hold amount %d", captureAmount, hold.getAmount()));
        }
    }

    public WalletTransaction saveTransaction(Wallet wallet, TransactionType type, String description,
                                             long amount, UUID referenceId) {
        WalletTransaction txn = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type(type)
                .amount(amount)
                .balanceAfter(wallet.getAvailableBalance())
                .description(description)
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .build();
        return walletTransactionRepository.save(txn);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .userId(wallet.getUserId())
                .availableBalance(wallet.getAvailableBalance())
                .heldBalance(wallet.getHeldBalance())
                .totalBalance(wallet.getTotalBalance())
                .frozen(wallet.isFrozen())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private HoldResponse toHoldResponse(BalanceHold hold) {
        return HoldResponse.builder()
                .holdId(hold.getId())
                .userId(hold.getUserId())
                .auctionId(hold.getAuctionId())
                .amount(hold.getAmount())
                .status(hold.getStatus().name())
                .createdAt(hold.getCreatedAt())
                .build();
    }

    private TransactionResponse toTransactionResponse(WalletTransaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .type(txn.getType().name())
                .amount(txn.getAmount())
                .description(txn.getDescription())
                .referenceId(txn.getReferenceId())
                .balanceAfter(txn.getBalanceAfter())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
