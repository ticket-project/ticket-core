package com.ticket.booking.internal.infrastructure.order.outbox;

import com.ticket.booking.internal.application.event.HoldLifecycleEventPublisher;
import com.ticket.booking.internal.application.event.HoldReleaseProgressRecorder;
import com.ticket.booking.internal.application.event.HoldReleaseRequest;
import com.ticket.booking.internal.domain.hold.model.Hold;
import com.ticket.booking.internal.infrastructure.order.outbox.create.HoldCreationOutbox;
import com.ticket.booking.internal.infrastructure.order.outbox.create.HoldCreationOutboxRepository;
import com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutbox;
import com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutboxTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * hold 생성·해제 후속 처리 이벤트를 outbox 테이블에 기록한다.
 *
 * <p>업무 트랜잭션과 같은 트랜잭션에서 저장되므로, 커밋된 주문에는 반드시 후속 처리 입력이 남는다.
 * 재시도 상태와 다음 시도 시각은 이 모듈이 정하고 app에는 식별자만 돌려준다.
 *
 * <p>{@link HoldReleaseProgressRecorder}까지 함께 구현하는 이유: hold 해제 outbox를 기록하는 저장소와
 * 그 outbox의 재시도 중간 진행 상태(Redis 해제 완료 여부)를 기록하는 저장소가 같은
 * {@code HoldReleaseOutbox} 테이블이기 때문이다. 두 포트를 분리해도 구현은 결국 같은 리포지토리를
 * 다시 조립해야 하므로, 여기서는 책임이 아니라 저장소 하나를 공유하는 것으로 보고 합쳐 둔다.
 */
@Component
@RequiredArgsConstructor
public class OutboxHoldLifecycleEventPublisher implements HoldLifecycleEventPublisher, HoldReleaseProgressRecorder {

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;
    private final com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final HoldReleaseOutboxTransactionService holdReleaseOutboxTransactionService;

    @Override
    public Long publishHoldCreated(final Hold hold, final LocalDateTime occurredAt) {
        return holdCreationOutboxRepository.save(HoldCreationOutbox.create(hold, occurredAt)).getId();
    }

    @Override
    public Long publishHoldReleased(final HoldReleaseRequest request, final LocalDateTime occurredAt) {
        final HoldReleaseOutbox outbox = holdReleaseOutboxRepository.save(HoldReleaseOutbox.create(
                request.performanceId(),
                request.holdKey(),
                request.seatIds(),
                occurredAt
        ));
        return outbox.getId();
    }

    @Override
    public void recordHoldReleased(final Long eventId, final LocalDateTime releasedAt) {
        holdReleaseOutboxTransactionService.markHoldReleased(eventId, releasedAt);
    }
}
