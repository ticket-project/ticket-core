package com.ticket.booking.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.EntityExistsException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.application.port.HoldReleaseProgressRecorder;

import lombok.RequiredArgsConstructor;

/**
 * {@link HoldReleaseProgressRecorder}의 JPA 구현이다. {@code eventId} 기본키가 동시 재시도에서도 같은 row가 하나만 만들어짐을
 * 보장한다.
 *
 * <p><b>기록은 자기 트랜잭션에서 곧바로 커밋한다({@code REQUIRES_NEW}).</b> "Redis 해제는 끝났다"는 사실은 그 뒤에 무엇이 실패하든 되돌아가면
 * 안 된다 — 되돌아가면 재시도에서 Redis 해제를 다시 수행한다. 호출자가 트랜잭션 안이든 밖이든 이 경계가 같도록 전파를 명시한다.
 *
 * <p>중복은 {@code save} 호출 시점이 아니라 flush/commit 시점에도 드러난다. {@link HoldReleaseProgress}가 {@code
 * Persistable}로 "항상 새 row"임을 알려 merge 대신 persist를 타게 하고, {@code saveAndFlush}로 그 시점을 이 메서드 안으로 당겨
 * 잡는다.
 */
@Component
@RequiredArgsConstructor
public class HoldReleaseProgressRecorderAdapter implements HoldReleaseProgressRecorder {
    private final SpringDataHoldReleaseProgressJpaRepository repository;

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isReleased(final UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHoldReleased(final UUID eventId, final LocalDateTime releasedAt) {
        if (repository.existsById(eventId)) {
            return;
        }
        try {
            repository.saveAndFlush(new HoldReleaseProgress(eventId, releasedAt));
        } catch (final DataIntegrityViolationException | EntityExistsException e) {
            // 동시 재시도로 같은 eventId가 이미 기록된 경우. 이미 해제로 기록됐으므로 무시한다.
        }
    }
}
