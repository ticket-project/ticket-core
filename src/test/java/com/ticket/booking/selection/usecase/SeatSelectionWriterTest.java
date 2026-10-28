package com.ticket.booking.selection.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.domain.SeatSelectionService;

/**
 * writer는 알림 이벤트를 만들어 선택 서비스에 넘기기만 한다. 선점 확인·상태 변경·발행의 원자성과 순서는 저장소 스크립트가 지키므로 {@code CoreRedisIntegrationTest}가 실제
 * Redis로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionWriterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    private SeatSelectionWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SeatSelectionWriter(seatSelectionService, performanceSeatRepository, CLOCK);
    }

    @Test
    void 선택하며_performanceSeatId와_지금_시각으로_SELECTED를_넘긴다() {
        writer.select(10L, 20L, 1L, 501L, NOW.plusMinutes(1), 4);

        verify(seatSelectionService)
                .select(10L, 20L, 1L, 4, new SeatStatusEvent(10L, 501L, 20L, SeatStatusAction.SELECTED, NOW));
    }

    @Test
    void 예매가_마감됐으면_선택하지_않는다() {
        assertThatThrownBy(() -> writer.select(10L, 20L, 1L, 501L, NOW.minusNanos(1), 4))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E3001);

        verifyNoInteractions(seatSelectionService);
    }

    @Test
    void 해제하며_performanceSeatId를_찾아_DESELECTED를_넘긴다() {
        givenPerformanceSeat();

        writer.deselect(10L, 20L, 1L);

        verify(seatSelectionService).deselect(10L, 20L, 1L, deselected(501L));
    }

    /** 여러 좌석을 한꺼번에 해제할 때 좌석마다 DB를 다시 읽지 않도록 미리 조회한 id를 그대로 받는다. */
    @Test
    void 일괄_해제는_받은_performanceSeatId를_그대로_쓴다() {
        writer.deselectIfOwned(10L, 20L, 1L, 501L);

        verify(seatSelectionService).deselectIfOwned(10L, 20L, 1L, deselected(501L));
        verify(performanceSeatRepository, never()).findAllByPerformanceIdAndSeatIdIn(anyLong(), anyList());
    }

    @Test
    void 만료_알림은_비어_있을_때만_발행하도록_넘긴다() {
        givenPerformanceSeat();

        writer.notifyReleasedIfFree(10L, 20L);

        verify(seatSelectionService).notifyIfFree(10L, 20L, deselected(501L));
    }

    @Test
    void 회차_판매_좌석을_찾지_못해도_알린다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L)))
                .thenReturn(List.of());

        writer.notifyReleasedIfFree(10L, 20L);

        verify(seatSelectionService).notifyIfFree(10L, 20L, deselected(null));
    }

    private SeatStatusEvent deselected(final Long performanceSeatId) {
        return new SeatStatusEvent(10L, performanceSeatId, 20L, SeatStatusAction.DESELECTED, NOW);
    }

    private void givenPerformanceSeat() {
        final PerformanceSeat performanceSeat =
                new PerformanceSeat(10L, 20L, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", 501L);
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L)))
                .thenReturn(List.of(performanceSeat));
    }
}
