package com.ticket.booking.selection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.selection.usecase.SeatSelectionWriter;

/** 이 핸들러는 Redis key 해석, 처리권 확보, 호출만 한다. 현재 상태 확인과 알림 필요 여부 판단은 {@code SeatSelectionWriterTest}가 고정한다. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
class SeatSelectionExpirationHandlerTest {
    @Mock
    private SeatSelectionWriter seatSelectionWriter;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Object> claimBucket;

    private SeatSelectionExpirationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SeatSelectionExpirationHandler(seatSelectionWriter, redissonClient);
    }

    @Test
    void 처리권을_잡으면_좌석_select_키를_해석해_application에_넘긴다() {
        final String expiredKey = SeatSelectionRedisKey.select(10L, 20L);
        when(redissonClient.getBucket(eq("expired-claim:" + expiredKey), any(Codec.class)))
                .thenReturn(claimBucket);
        when(claimBucket.setIfAbsent(anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);

        assertThat(handler.handle(expiredKey)).isTrue();

        verify(seatSelectionWriter).notifyReleasedIfFree(10L, 20L);
    }

    /** 다른 인스턴스가 먼저 잡았다. relay로 그 인스턴스의 알림이 모든 사용자에게 가므로 여기서는 처리하지 않는다. */
    @Test
    void 처리권을_못_잡으면_처리하지_않고_끝낸다() {
        final String expiredKey = SeatSelectionRedisKey.select(10L, 20L);
        when(redissonClient.getBucket(eq("expired-claim:" + expiredKey), any(Codec.class)))
                .thenReturn(claimBucket);
        when(claimBucket.setIfAbsent(anyString(), eq(Duration.ofSeconds(60)))).thenReturn(false);

        assertThat(handler.handle(expiredKey)).isTrue();

        verifyNoInteractions(seatSelectionWriter);
    }

    /** 모르는 키(표시 키 자신의 만료 포함)까지 claim하면 표시 키 만료가 다시 claim되는 고리가 생긴다. */
    @Test
    void 좌석_select_키가_아니면_처리권도_잡지_않는다() {
        assertThat(handler.handle("unknown:key")).isFalse();
        assertThat(handler.handle("expired-claim:" + SeatSelectionRedisKey.select(10L, 20L)))
                .isFalse();

        verifyNoInteractions(seatSelectionWriter, redissonClient);
    }

    /** 같은 좌석의 선택 키가 다시 만료되기 전에 claim이 사라져야 다음 만료를 막지 않는다. 선택 TTL을 줄이면 claim TTL도 함께 줄인다. */
    @Test
    void claim_TTL은_선택_TTL보다_짧다() {
        assertThat(SeatSelectionExpirationHandler.CLAIM_TTL).isLessThan(SeatSelectionService.SELECT_TTL);
    }
}
