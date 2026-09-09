package com.ticket.booking.infrastructure;

import com.ticket.booking.application.LockKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 락 key 형식을 고정한다. 형식이 바뀌면 배포 중 기존 락과 겹치지 않아
 * 같은 좌석을 두 인스턴스가 동시에 잡을 수 있다.
 */
@SuppressWarnings("NonAsciiCharacters")
class RedissonLockKeyFormatterTest {

    private final RedissonLockKeyFormatter formatter = new RedissonLockKeyFormatter();

    @Test
    void 좌석_락은_회차와_좌석으로_key를_만든다() {
        assertThat(formatter.format(LockKey.seat(10L, 100L))).isEqualTo("LOCK:hold:10:100");
    }

    @Test
    void 주문_시작_락은_회원과_회차로_key를_만든다() {
        assertThat(formatter.format(LockKey.orderStart(20L, 10L))).isEqualTo("LOCK:start-order:20:10");
    }

}
