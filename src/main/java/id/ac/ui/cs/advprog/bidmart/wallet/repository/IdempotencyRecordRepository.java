package id.ac.ui.cs.advprog.bidmart.wallet.repository;

import id.ac.ui.cs.advprog.bidmart.wallet.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
