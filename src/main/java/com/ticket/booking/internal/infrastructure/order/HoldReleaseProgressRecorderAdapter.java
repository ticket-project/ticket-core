package com.ticket.booking.internal.infrastructure.order;

import com.ticket.booking.internal.application.event.HoldReleaseProgressRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@link HoldReleaseProgressRecorder}의 JPA 구현이다. {@code eventId} 기본키가 동시 재시도에서도
 * 같은 row가 하나만 만들어짐을 보장한다.
 */
@Component
@RequiredArgsConstructor
public class HoldReleaseProgressRecorderAdapter implements HoldReleaseProgressRecorder {

    private final SpringDataHoldReleaseProgressJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isReleased(final UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    @Transactional
    public void recordHoldReleased(final UUID eventId, final LocalDateTime releasedAt) {
        try {
            repository.save(new HoldReleaseProgress(eventId, releasedAt));
        } catch (final DataIntegrityViolationException e) {
            // 동시 재시도로 같은 eventId가 이미 기록된 경우. 이미 해제로 기록됐으므로 무시한다.
        }
    }
}
