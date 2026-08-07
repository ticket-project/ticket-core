package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.release.HoldReleaseOutbox;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxExecutor;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxRepository;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxStatus;

import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldReleaseOutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final List<HoldReleaseOutboxStatus> RETRYABLE_STATUSES = List.of(
            HoldReleaseOutboxStatus.PENDING,
            HoldReleaseOutboxStatus.FAILED
    );

    private final HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final HoldReleaseOutboxExecutor holdReleaseOutboxExecutor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "120000")
    @DistributedLock(
            prefix = "hold-release-outbox",
            dynamicKey = "'batch'",
            leaseTime = 60000L,
            message = "hold release outbox 처리 중입니다. 잠시 후 다시 시도해 주세요."
    )
    public void processPendingHoldReleases() {
        while (true) {
            final Slice<HoldReleaseOutbox> dueOutboxes = holdReleaseOutboxRepository.findAllByStatusInAndNextAttemptAtLessThanEqual(
                    RETRYABLE_STATUSES,
                    LocalDateTime.now(clock),
                    PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"))
            );
            if (!dueOutboxes.hasContent()) {
                return;
            }

            for (final HoldReleaseOutbox outbox : dueOutboxes.getContent()) {
                try {
                    holdReleaseOutboxExecutor.process(outbox.getId(), LocalDateTime.now(clock));
                } catch (final RuntimeException e) {
                    log.warn("hold release outbox 실행을 시작하지 못했습니다. outboxId={}", outbox.getId(), e);
                }
            }

            if (dueOutboxes.getNumberOfElements() < BATCH_SIZE) {
                return;
            }
        }
    }
}
