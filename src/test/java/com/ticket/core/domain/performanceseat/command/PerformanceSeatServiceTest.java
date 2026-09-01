package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceSeatServiceTest {

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @InjectMocks
    private PerformanceSeatService performanceSeatService;

    @Test
    void 가용한_공연좌석만_조회한다() {
        //given
        List<PerformanceSeat> seats = List.of(org.mockito.Mockito.mock(PerformanceSeat.class));
        when(performanceSeatRepository.findAllByStateEquals(PerformanceSeatState.AVAILABLE)).thenReturn(seats);

        //when
        List<PerformanceSeat> result = performanceSeatService.getAllAvailableSeats();

        //then
        assertThat(result).isEqualTo(seats);
        verify(performanceSeatRepository).findAllByStateEquals(PerformanceSeatState.AVAILABLE);
    }
}

