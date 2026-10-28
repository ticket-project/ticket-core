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

/** "아직 이 hold인가" 확인과 선택 정리·발행의 원자성은 {@code RedissonHoldStoreIntegrationTest}가 실제 Redis로 고정한다. 여기서는 알림 내용만 본다. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HeldSeatPublisherTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private HoldStore holdStore;

    @Test
    void 좌석마다_performanceSeatId를_실은_HELD로_확정을_넘긴다() {
        new HeldSeatPublisher(holdStore, CLOCK)
                .clearSelectionsAndPublishHeld(
                        "hold-key",
                        20L,
                        new OrderHoldSnapshot(10L, List.of(100L, 200L), Map.of(100L, 901L, 200L, 902L)));

        verify(holdStore)
                .confirmHeld(
                        10L,
                        "hold-key",
                        20L,
                        List.of(
                                new SeatStatusEvent(10L, 901L, 100L, SeatStatusAction.HELD, NOW),
                                new SeatStatusEvent(10L, 902L, 200L, SeatStatusAction.HELD, NOW)));
    }
}
