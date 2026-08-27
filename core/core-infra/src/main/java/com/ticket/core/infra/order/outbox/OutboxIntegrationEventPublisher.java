package com.ticket.core.infra.order.outbox;

import com.ticket.core.app.event.HoldReleaseProgressRecorder;
import com.ticket.core.app.event.IntegrationEventPublisher;
import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.infra.order.outbox.create.HoldCreationOutbox;
import com.ticket.core.infra.order.outbox.create.HoldCreationOutboxRepository;
import com.ticket.core.infra.order.outbox.release.HoldReleaseOutbox;
import com.ticket.core.infra.order.outbox.release.HoldReleaseOutboxTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 후속 처리 이벤트를 outbox 테이블에 기록한다.
 *
 * <p>업무 트랜잭션과 같은 트랜잭션에서 저장되므로, 커밋된 주문에는 반드시 후속 처리 입력이 남는다.
 * 재시도 상태와 다음 시도 시각은 이 모듈이 정하고 app에는 식별자만 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class OutboxIntegrationEventPublisher implements IntegrationEventPublisher, HoldReleaseProgressRecorder {

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;
    private final com.ticket.core.infra.order.outbox.release.HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final HoldReleaseOutboxTransactionService holdReleaseOutboxTransactionService;

    @Override
    public Long publishHoldCreated(final Hold hold, final LocalDateTime occurredAt) {
        return holdCreationOutboxRepository.save(HoldCreationOutbox.create(hold, occurredAt)).getId();
    }

    @Override
    public Long publishHoldReleased(final OrderTerminationResult result, final LocalDateTime occurredAt) {
        final HoldReleaseOutbox outbox = holdReleaseOutboxRepository.save(HoldReleaseOutbox.create(
                result.performanceId(),
                result.holdKey(),
                result.seatIds(),
                occurredAt
        ));
        return outbox.getId();
    }

    @Override
    public void recordHoldReleased(final Long eventId, final LocalDateTime releasedAt) {
        holdReleaseOutboxTransactionService.markHoldReleased(eventId, releasedAt);
    }
}
