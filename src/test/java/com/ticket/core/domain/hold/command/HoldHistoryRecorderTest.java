package com.ticket.core.domain.hold.command;

import com.ticket.core.domain.hold.model.HoldHistory;
import com.ticket.core.domain.hold.repository.HoldHistoryRepository;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.hold.model.HoldHistoryEventType;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HoldHistoryRecorderTest {

    @Mock
    private HoldHistoryRepository holdHistoryRepository;

    @InjectMocks
    private HoldHistoryRecorder holdHistoryRecorder;

    @Test
    void 선택한_좌석마다_created_hold_history를_기록한다() {
        //given
        PerformanceSeat first = createPerformanceSeat(100L, 10L);
        PerformanceSeat second = createPerformanceSeat(101L, 20L);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        //when
        holdHistoryRecorder.recordCreated(
                1L,
                2L,
                "hold-key",
                occurredAt,
                expiresAt,
                List.of(first, second)
        );

        //then
        List<HoldHistory> histories = captureHistories();
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getEventType()).isEqualTo(HoldHistoryEventType.CREATED);
        assertThat(histories.get(0).getOccurredAt()).isEqualTo(occurredAt);
        assertThat(histories.get(0).getExpiresAt()).isEqualTo(expiresAt);
        assertThat(histories.get(1).getPerformanceSeatId()).isEqualTo(101L);
        assertThat(histories.get(1).getSeatId()).isEqualTo(20L);
    }

    @Test
    void 주문취소시_좌석마다_canceled_hold_history를_기록한다() {
        //given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 10);
        OrderSeat first = createOrderSeat(100L, 10L);
        OrderSeat second = createOrderSeat(101L, 20L);

        //when
        holdHistoryRecorder.recordCanceled(
                1L,
                2L,
                "hold-key",
                occurredAt,
                List.of(first, second)
        );

        //then
        List<HoldHistory> histories = captureHistories();
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getEventType()).isEqualTo(HoldHistoryEventType.CANCELED);
        assertThat(histories.get(0).getOccurredAt()).isEqualTo(occurredAt);
        assertThat(histories.get(0).getReleaseReason()).isEqualTo(HoldReleaseReason.USER_CANCELED);
    }

    @Test
    void 주문만료시_좌석마다_expired_hold_history를_기록한다() {
        //given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        OrderSeat first = createOrderSeat(100L, 10L);

        //when
        holdHistoryRecorder.recordExpired(
                1L,
                2L,
                "hold-key",
                occurredAt,
                List.of(first)
        );

        //then
        List<HoldHistory> histories = captureHistories();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getEventType()).isEqualTo(HoldHistoryEventType.EXPIRED);
        assertThat(histories.get(0).getOccurredAt()).isEqualTo(occurredAt);
        assertThat(histories.get(0).getReleaseReason()).isEqualTo(HoldReleaseReason.TTL_EXPIRED);
    }

    @SuppressWarnings("unchecked")
    private List<HoldHistory> captureHistories() {
        ArgumentCaptor<List<HoldHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(holdHistoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private PerformanceSeat createPerformanceSeat(final Long performanceSeatId, final Long seatId) {
        PerformanceSeat performanceSeat = mock(PerformanceSeat.class);
        Seat seat = mock(Seat.class);
        when(performanceSeat.getId()).thenReturn(performanceSeatId);
        when(performanceSeat.getSeat()).thenReturn(seat);
        when(seat.getId()).thenReturn(seatId);
        return performanceSeat;
    }

    private OrderSeat createOrderSeat(final Long performanceSeatId, final Long seatId) {
        OrderSeat orderSeat = mock(OrderSeat.class);
        when(orderSeat.getPerformanceSeatId()).thenReturn(performanceSeatId);
        when(orderSeat.getSeatId()).thenReturn(seatId);
        return orderSeat;
    }
}
