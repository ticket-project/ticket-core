package com.ticket.booking.event;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.usecase.OrderHoldSnapshot;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;

/** 해제와 "비었는가" 확인·발행의 원자성은 저장소 스크립트가 지키므로 {@code RedissonHoldStoreIntegrationTest}가 실제 Redis로 고정한다. 여기서는 알림 내용만 본다. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaserTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private HoldStore holdStore;

    @Test
    void 좌석마다_performanceSeatId를_실은_RELEASED로_해제를_넘긴다() {
        new HoldReleaser(holdStore, CLOCK)
                .releaseAndPublish(
                        "old-hold", new OrderHoldSnapshot(1L, List.of(10L, 20L), Map.of(10L, 910L, 20L, 920L)));

        verify(holdStore)
                .release(
                        1L,
                        "old-hold",
                        List.of(
                                new SeatStatusEvent(1L, 910L, 10L, SeatStatusAction.RELEASED, NOW),
                                new SeatStatusEvent(1L, 920L, 20L, SeatStatusAction.RELEASED, NOW)));
    }
}
