package com.ticket.core.domain.order.command.create;

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

public interface HoldCreationOutboxRepository extends JpaRepository<HoldCreationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from HoldCreationOutbox o
            where o.id = :outboxId
            """)
    Optional<HoldCreationOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);

    long countByStatus(HoldCreationOutboxStatus status);

    Optional<HoldCreationOutbox> findFirstByStatusInOrderByCreatedAtAsc(
            Collection<HoldCreationOutboxStatus> statuses
    );

    Slice<HoldCreationOutbox> findAllByStatusInAndNextAttemptAtLessThanEqual(
            Collection<HoldCreationOutboxStatus> statuses,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );
}
