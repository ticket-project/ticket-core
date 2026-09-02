package com.ticket.booking.internal.application.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 테스트용 {@link LockManager}다. 실제로 잠그지 않고 무엇을 어떤 조건으로 잠그려 했는지 기록한다.
 *
 * <p>락 획득 실패를 흉내 내려면 {@link #failWith(RuntimeException)}로 던질 예외를 지정한다.
 */
public class RecordingLockManager implements LockManager {

    private final List<Acquisition> acquisitions = new ArrayList<>();
    private RuntimeException failure;

    public record Acquisition(List<LockKey> keys, LockOptions options) {
    }

    @Override
    public <T> T withLock(final List<LockKey> keys, final LockOptions options, final Supplier<T> action) {
        acquisitions.add(new Acquisition(List.copyOf(keys), options));
        if (failure != null) {
            throw failure;
        }
        return action.get();
    }

    public void failWith(final RuntimeException exception) {
        this.failure = exception;
    }

    public List<Acquisition> acquisitions() {
        return List.copyOf(acquisitions);
    }

    public Acquisition lastAcquisition() {
        if (acquisitions.isEmpty()) {
            throw new IllegalStateException("잠금을 시도한 적이 없습니다.");
        }
        return acquisitions.getLast();
    }

    public List<LockKey> allKeys() {
        return acquisitions.stream().flatMap(acquisition -> acquisition.keys().stream()).toList();
    }
}
