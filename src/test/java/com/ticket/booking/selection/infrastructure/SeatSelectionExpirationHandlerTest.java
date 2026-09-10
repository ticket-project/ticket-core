package com.ticket.booking.selection.infrastructure;

import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.selection.infrastructure.SeatSelectionRedisKey;
import com.ticket.booking.application.SeatStatusEventPublisher;
import com.ticket.booking.application.SeatStatusEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionExpirationHandlerTest {

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Test
    void 좌석_select_키를_지원하고_performanceSeatId로_deselected_이벤트를_발행한다() {
        SeatSelectionExpirationHandler handler =
                new SeatSelectionExpirationHandler(seatEventPublisher, performanceSeatRepository);
        String expiredKey = SeatSelectionRedisKey.select(10L, 20L);
        PerformanceSeat performanceSeat = new PerformanceSeat(10L, 20L, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", 501L);
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L)))
                .thenReturn(List.of(performanceSeat));

        assertThat(handler.supports(expiredKey)).isTrue();

        handler.handle(expiredKey);

        verify(seatEventPublisher).publish(10L, 501L, SeatStatusEvent.SeatStatusAction.DESELECTED);
    }

    @Test
    void 좌석_select_키가_아니면_지원하지_않는다() {
        SeatSelectionExpirationHandler handler =
                new SeatSelectionExpirationHandler(seatEventPublisher, performanceSeatRepository);

        assertThat(handler.supports("unknown:key")).isFalse();
        verifyNoInteractions(seatEventPublisher, performanceSeatRepository);
    }
}
