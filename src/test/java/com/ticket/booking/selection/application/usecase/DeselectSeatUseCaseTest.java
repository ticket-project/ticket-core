package com.ticket.booking.selection.application.usecase;

import com.ticket.booking.application.SeatStatusEvent;

import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatState;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.SeatStatusEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectSeatUseCaseTest {

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    @InjectMocks
    private DeselectSeatUseCase useCase;

    @Test
    void deselect_then_publish_deselected_event_with_performanceSeatId() {
        DeselectSeatUseCase.Input input = new DeselectSeatUseCase.Input(10L, 20L, 1L);
        PerformanceSeat performanceSeat =
                new PerformanceSeat(10L, 20L, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(performanceSeat, "id", 501L);
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L)))
                .thenReturn(List.of(performanceSeat));

        useCase.execute(input);

        InOrder inOrder = inOrder(seatSelectionService, seatEventPublisher);
        inOrder.verify(seatSelectionService).deselect(10L, 20L, 1L);
        // 외부 판매 좌석 식별자는 seatId가 아니라 performanceSeatId다.
        inOrder.verify(seatEventPublisher).publish(10L, 501L, SeatStatusAction.DESELECTED);
    }
}
