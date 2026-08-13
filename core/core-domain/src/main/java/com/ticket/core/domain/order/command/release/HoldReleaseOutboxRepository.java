package com.ticket.core.domain.order.command.release;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface HoldReleaseOutboxRepository extends JpaRepository<HoldReleaseOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from HoldReleaseOutbox o
            where o.id = :outboxId
            """)
    Optional<HoldReleaseOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);

    Slice<HoldReleaseOutbox> findAllByStatusInAndNextAttemptAtLessThanEqual(
            Collection<HoldReleaseOutboxStatus> statuses,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );
}
