package com.ticket.core.app.performanceseat.command;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.core.app.performanceseat.event.SeatStatusEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import java.util.Optional;

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
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(seatSelectionService.deselectAll(10L, 1L)).thenReturn(DeselectedSeatIds.from(List.of(20L, 21L)));

        useCase.execute(new DeselectAllSeatsUseCase.Input(10L, 1L));

        verify(memberRepository).findActiveById(1L);
        verify(seatSelectionService).deselectAll(10L, 1L);
        verify(seatEventPublisher).publish(10L, 20L, SeatStatusAction.DESELECTED);
        verify(seatEventPublisher).publish(10L, 21L, SeatStatusAction.DESELECTED);
        verify(seatEventPublisher, times(2)).publish(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
