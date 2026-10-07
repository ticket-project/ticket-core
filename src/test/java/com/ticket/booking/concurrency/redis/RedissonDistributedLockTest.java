package com.ticket.booking.concurrency.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.LockOptions;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockTest {
    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private RedissonDistributedLock distributedLock() {
        return new RedissonDistributedLock(redissonClient, new RedissonLockKeyFormatter());
    }

    @Test
    void 임대시간_없이_watchdog_방식으로_잠근다() throws InterruptedException {
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(500L, TimeUnit.MILLISECONDS)).thenReturn(true);

        distributedLock()
                .withLock(List.of(LockKey.seat(10L, 100L)), LockOptions.waiting(Duration.ofMillis(500)), () -> {});

        verify(lock).tryLock(500L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void 락을_얻지_못하면_작업을_실행하지_않고_HOLD_BUSY로_끊는다() throws InterruptedException {
        when(redissonClient.getLock("LOCK:hold:10:100")).thenReturn(lock);
        when(lock.tryLock(any(Long.class), any(TimeUnit.class))).thenReturn(false);
        final AtomicBoolean executed = new AtomicBoolean(false);

        assertThatThrownBy(() -> distributedLock()
                        .withLock(
                                List.of(LockKey.seat(10L, 100L)),
                                LockOptions.defaults().withFailureMessage("좌석 처리 중입니다."),
                                () -> executed.set(true)))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E6003)
                // 응답 메시지는 오류 카탈로그가 정하고, 지정한 문구는 data로 함께 전달한다.
                .hasFieldOrPropertyWithValue("data", "좌석 처리 중입니다.");

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

        distributedLock().withLock(LockKey.seats(10L, List.of(100L, 20L)), LockOptions.defaults(), () -> {});

        verify(multiLock).tryLock(5_000L, TimeUnit.MILLISECONDS);
        verify(multiLock).unlock();
        verify(first, never()).tryLock(any(Long.class), any(TimeUnit.class));
    }

    @Test
    void 잠글_대상이_없으면_실행하지_않는다() {
        assertThatThrownBy(() -> distributedLock().withLock(List.of(), LockOptions.defaults(), () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
