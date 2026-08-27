package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage.SeatAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectAllSeatsUseCaseTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    @InjectMocks
    private DeselectAllSeatsUseCase useCase;

    @Test
    void deselect_all_then_publish_each_seat() {
        when(seatSelectionService.deselectAll(10L, 1L)).thenReturn(DeselectedSeatIds.from(List.of(20L, 21L)));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(memberRepository).getActiveById(1L);
        verify(seatSelectionService).deselectAll(10L, 1L);
        verify(seatEventPublisher).publish(10L, 20L, SeatAction.DESELECTED);
        verify(seatEventPublisher).publish(10L, 21L, SeatAction.DESELECTED);
        verify(seatEventPublisher, times(2)).publish(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
