package com.ticket.booking.internal.infrastructure.lock;

import com.ticket.booking.internal.application.lock.LockKey;
import com.ticket.booking.internal.application.lock.LockOptions;
import com.ticket.booking.internal.exception.HoldBusyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RedissonLockManagerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private RedissonLockManager lockManager() {
        return new RedissonLockManager(redissonClient, new RedissonLockKeyFormatter());
    }

    @Test
    void 임대시간을_지정하지_않으면_watchdog_방식으로_잠근다() throws InterruptedException {
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(500L, TimeUnit.MILLISECONDS)).thenReturn(true);

        lockManager().withLock(
                List.of(LockKey.seat(10L, 100L)),
                LockOptions.waiting(Duration.ofMillis(500)),
                () -> {
                }
        );

        verify(lock).tryLock(500L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void 임대시간을_지정하면_그_시간_뒤에_자동_해제되도록_잠근다() throws InterruptedException {
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(5_000L, 60_000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        lockManager().withLock(
                List.of(LockKey.seat(10L, 100L)),
                LockOptions.defaults().withLeaseTime(Duration.ofSeconds(60)),
                () -> {
                }
        );

        verify(lock).tryLock(5_000L, 60_000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void 락을_얻지_못하면_작업을_실행하지_않고_HOLD_BUSY로_끊는다() throws InterruptedException {
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(any(Long.class), any(TimeUnit.class))).thenReturn(false);
        final AtomicBoolean executed = new AtomicBoolean(false);

        assertThatThrownBy(() -> lockManager().withLock(
                List.of(LockKey.seat(10L, 100L)),
                LockOptions.defaults().withFailureMessage("좌석 처리 중입니다."),
                () -> executed.set(true)
        ))
                .isInstanceOf(HoldBusyException.class)
                .satisfies(thrown -> {
                    assertThat(thrown).isInstanceOf(HoldBusyException.class);
                    // 응답 메시지는 오류 카탈로그가 정하고, 지정한 문구는 data로 함께 전달한다.
                    assertThat(((HoldBusyException) thrown).getData()).isEqualTo("좌석 처리 중입니다.");
                });

        assertThat(executed).isFalse();
    }

    @Test
    void 여러_좌석은_정렬해_한_번에_잠가_데드락을_피한다() throws InterruptedException {
        final RLock first = org.mockito.Mockito.mock(RLock.class);
        final RLock second = org.mockito.Mockito.mock(RLock.class);
        final RLock multiLock = org.mockito.Mockito.mock(RLock.class);
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(first);
        when(redissonClient.getLock("LOCK:hold:10:20")).thenReturn(second);
        when(redissonClient.getMultiLock(any(RLock[].class))).thenReturn(multiLock);
        when(multiLock.tryLock(5_000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        lockManager().withLock(
                LockKey.seats(10L, List.of(100L, 20L)),
                LockOptions.defaults(),
                () -> {
                }
        );

        verify(multiLock).tryLock(5_000L, TimeUnit.MILLISECONDS);
        verify(multiLock).unlock();
        verify(first, never()).tryLock(any(Long.class), any(TimeUnit.class));
    }

    @Test
    void 잠글_대상이_없으면_실행하지_않는다() {
        assertThatThrownBy(() -> lockManager().withLock(List.of(), LockOptions.defaults(), () -> {
        }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
