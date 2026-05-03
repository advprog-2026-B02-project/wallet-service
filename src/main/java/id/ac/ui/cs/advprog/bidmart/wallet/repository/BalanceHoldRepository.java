package id.ac.ui.cs.advprog.bidmart.wallet.repository;

import id.ac.ui.cs.advprog.bidmart.wallet.model.BalanceHold;
import id.ac.ui.cs.advprog.bidmart.wallet.model.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceHoldRepository extends JpaRepository<BalanceHold, UUID> {
    List<BalanceHold> findByAuctionIdAndStatus(UUID auctionId, HoldStatus status);
    Optional<BalanceHold> findByUserIdAndAuctionIdAndStatus(UUID userId, UUID auctionId, HoldStatus status);
    List<BalanceHold> findAllByWalletId(UUID walletId);
}
